/**
 * Thuật toán FD-SPMSketch cải tiến — LC-FD-SPMSketch
 * Khai thác mẫu trình tự thường xuyên trong CSDL hoàn toàn động.
 * Cải tiến: CSR PosMap phẳng, gap+varint nén, lazy PosMap, buildAdd O(1).
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;

import utils.MemoryLogger;


public class algoFpmMros {
    // ── Biến thống kê ──
    private static long startTime;  // thời điểm bắt đầu
    private static long endTime;    // thời điểm kết thúc
    static int patternCount  = 0;   // số pattern thường xuyên tìm được
    static int add_FreCount  = 0;   // số pattern tăng thêm sau Insert
    static int fully_FreCount= 0;   // số pattern sau FullyMFP
    static int withPurnCount = 0;   // số lần qua được bước prune sketch
    static int extension     = 0;


    // CSDL dọc 1-pattern: key = itemName, value = VerDB_Mros (sids/pids CSR)
    static Map<Integer,VerDB_Mros> one_verDBList = new HashMap<>();
    static Map<Integer,VerDB_Mros> addItemMap = new HashMap<>();
    // Buffer pattern thường xuyên (k>=1): key = danh sách item, value = Mros sketch
    static Map<List<Integer>,Mros> k_MDBmap = new HashMap<>();

    // Buffer pattern bán thường xuyên (semi-frequent): key = pattern, value = Mros sketch
    static Map<List<Integer>,Mros> semi_MDBmap = new HashMap<>();
    // Buffer pattern có thể sẽ thường xuyên sau Insert: key = pattern, value = Mros sketch
    static Map<List<Integer>,Mros> maybe_MDBmap = new HashMap<>();


    // Ngưỡng support tối thiểu (tuyệt đối)
    static double minSup = 0;
    // Tổng số chuỗi đã đọc
    static int Maxsid = 0;
    // Mros theo dõi tập SID hiện tại (dùng cho Delete)
    static Mros M_ucid_de = new Mros(10, 16, 0.38, 8);

    // Đối tượng ghi file kết quả
    BufferedWriter writer = null;
    static BufferedWriter deWriter = null;

    // Tập 1-pattern thường xuyên ban đầu
    static Set<Integer> freItemList  = new HashSet<>();
    // Tập pattern thêm mới sau Insert
    static Set<List<Integer>> addFreList = new HashSet<>();
    // Tập 1-pattern bán thường xuyên ban đầu
    static Set<Integer> semiItemList = new HashSet<>();
    // Tập 1-pattern không thường xuyên
    static Set<Integer> unFreList    = new HashSet<>();
    // Tỉ lệ delta (ngưỡng bán thường xuyên = minSup * delta)
    static double delta = 0;
    // Danh sách 2-pattern thường xuyên
    static List<List<Integer>> two_freItemList = new ArrayList<>();


    // Danh sách candidate cho mỗi item (dùng trong GrowP)
    // key = item cuối của prefix, value = danh sách item có thể mở rộng tiếp
    static Map<Integer, List<Integer>> itemCadMap   = new HashMap<>();
    static Map<Integer, List<Integer>> itemCad_temp = new HashMap<>();


    /**
     * Đọc file dữ liệu gốc, xây dựng CSDL dọc 1-pattern, sau đó khai phá
     * toàn bộ pattern thường xuyên bằng GrowP.
     */
    public void ReadFileToVerDB_Mros(String input,String outputFilePath,double minSupRe,double delta) throws IOException {
        //MemoryLogger.getInstance().reset();// reset bộ nhớ trước khi bắt đầu đo
        writer =  new BufferedWriter(new FileWriter(outputFilePath));// tạo đối tượng ghi file
        //startTime = System.currentTimeMillis();
        MemoryLogger.getInstance().reset();
        int pidsum =0;// tổng số vị trí
        double variance=0;
        // BƯỚC 1: Đọc file từng dòng, xây dựng VerDB_Mros cho mỗi 1-pattern
        try {
            // Mở file đầu vào
            FileInputStream fin = new FileInputStream(new File(input));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = 0;     // chỉ số chuỗi hiện tại
            int pid = 0;     // vị trí item trong chuỗi
            double subvar = 0;
            // Đọc từng dòng cho đến hết file
            while ((thisLine = reader.readLine()) != null) {
                // Bỏ qua dòng trống hoặc comment
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                for (String token : thisLine.split(" ")) {
                    if (token.equals("-1")) {
                        pid++; // kết thúc 1 itemset trong chuỗi
                    } else if (token.equals("-2")) {
                        sid++;          // kết thúc 1 chuỗi
                        Maxsid = sid;
                        pidsum += pid;
                        subvar = (pid - 51.997) * (pid - 51.997);
                        variance += subvar;
                        pid = 0;
                        M_ucid_de.add(sid); // cập nhật Mros tổng SID
                    } else {
                        int itemName = Integer.parseInt(token);
                        // [CẢI TIẾN] buildAdd: O(1) amortized, không boxing
                        VerDB_Mros v = one_verDBList.get(itemName);
                        if (v == null) {
                            v = new VerDB_Mros();
                            one_verDBList.put(itemName, v);
                        }
                        // Chỉ add vào Mros khi sid thực sự mới với item này
                        if (v._curSid != sid) {
                            v.UCID_Mros.add(sid);
                        }
                        v.buildAdd(sid, pid);
                    }
                }
            }
            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        // [CẢI TIẾN] Finalize tất cả VerDB_Mros: chuyển từ build buffers sang int[] sids/pids
        for (VerDB_Mros v : one_verDBList.values()) { v.freeze(); }
        System.out.println("variance: "+variance);

        /*
         * Tính ngưỡng support tối thiểu tuyệt đối
         */
        minSup = minSupRe * Maxsid;
        if (minSup == 0) {
            minSup = 1;
        }

        // BƯỚC 2: Phân loại 1-pattern thành frequent / semi-frequent / infrequent
        Iterator<Entry<Integer, VerDB_Mros>> iter = one_verDBList.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, VerDB_Mros> entry = iter.next();
            if(entry.getValue().getSupport() >= minSup){
                freItemList.add(entry.getKey());
                List<Integer> p = new ArrayList<>();
                p.add(entry.getKey());
                k_MDBmap.put(p,entry.getValue().UCID_Mros);
                patternCount++;
                //unFreVerList.put(entry.getKey(),entry.getValue());
                //iter.remove(); // đã được loại (commented out)
            } else if (entry.getValue().getSupport() >= minSup*delta) {
                List<Integer> p = new ArrayList<>();
                p.add(entry.getKey());
                semi_MDBmap.put(p,entry.getValue().UCID_Mros);
                semiItemList.add(entry.getKey());
            } else {
                unFreList.add(entry.getKey());
            }
        }


        // BƯỚC 3: Xây dựng candidate list và sinh 2-pattern bằng ExtendP
        Map<List<Integer>,VerDB_Mros> k_verDB_temp = new HashMap<>();
        Map<List<Integer>,VerDB_Mros> semi_verDB_temp = new HashMap<>();
        List<List<Integer>> semi_twolist = new ArrayList<>();
        Set<Integer> union =new HashSet<>();
        union.addAll(freItemList);
        union.addAll(semiItemList);
        for (Integer integer1 : union){
            VerDB_Mros verDBMros_1 =one_verDBList.get(integer1);
            List<Integer> candlist =new ArrayList<>();
            for (Integer integer2 : union){
                List<Integer> itemList = new ArrayList<>();
                itemList.add(integer1);
                itemList.add(integer2);
                VerDB_Mros verDBMros_2 =one_verDBList.get(integer2);
                VerDB_Mros verDBMros_xy = ExtendP(verDBMros_1,verDBMros_2,minSup);
                if (verDBMros_xy.cnt>=minSup){
                    two_freItemList.add(itemList);
                    k_verDB_temp.put(itemList,verDBMros_xy);
                    k_MDBmap.put(itemList,verDBMros_xy.UCID_Mros);
                    candlist.add(integer2);
                }else if (verDBMros_xy.cnt>=minSup*delta){
                    semi_twolist.add(itemList);
                    semi_verDB_temp.put(itemList,verDBMros_xy);
                    semi_MDBmap.put(itemList,verDBMros_xy.UCID_Mros);
                    candlist.add(integer2);
                }
                itemCadMap.put(integer1,candlist);
            }
        }


        // BƯỚC 4: Mở rộng từ 2-pattern lên k-pattern bằng GrowP đệ quy
        for (List<Integer> two_integers : two_freItemList){
            Integer last = two_integers.get(two_integers.size()-1);
            List<Integer> canList_1 = itemCadMap.get(last);
            VerDB_Mros verDBMros_1 = k_verDB_temp.get(two_integers);
            if (canList_1 != null){
                GrowP(two_integers,verDBMros_1,canList_1,minSup,delta);
            }
            //savePattern(prefixVerList,minSup);
        }

        for (List<Integer> semiPtwo : semi_twolist){
            Integer last = semiPtwo.get(semiPtwo.size()-1);
            List<Integer> canList_1 = itemCadMap.get(last);
            VerDB_Mros verDBMros_1 = semi_verDB_temp.get(semiPtwo);
            if (canList_1!=null){
                GrowP(semiPtwo,verDBMros_1,canList_1,minSup,delta);
            }
        }
        //MemoryLogger.getInstance().checkMemory();
        //endTime = System.currentTimeMillis();
        //printStatistics();
        //System.out.println("max past memory:"+MemoryLogger.getInstance().getMaxMemory());
    }





    /**
     * GrowP: mở rộng prefix fre_x với từng candidate y.
     * Cải tiến 2.3.1/2.3.2: recurse ngay sau khi tạo verDB_xy (không tích lũy),
     * giúp verDB_xy của sibling trước được GC trước khi tạo sibling tiếp theo.
     * @param fre_x        prefix hiện tại
     * @param VerDB_x      VerDB_Mros của fre_x (CSR format)
     * @param candidateList danh sách item có thể nối vào fre_x
     * @param minSup       ngưỡng support tối thiểu
     * @param delta        hệ số ngưỡng bán thường xuyên
     */

    public static void GrowP(List<Integer> fre_x,VerDB_Mros VerDB_x,List<Integer> candidateList,double minSup,double delta) throws IOException{
        Mros Mros_x = VerDB_x.UCID_Mros;
        // [CẢI TIẾN] item_new chỉ tạo sau khi verDB_xy pass threshold (tránh alloc lãng phí)
        // [CẢI TIẾN] Recurse ngay thay vì tích lũy vào freVerList_new
        //   → sibling VerDB_xy được GC trước khi tạo sibling tiếp theo
        for (Integer y : candidateList) {
            Mros Mros_y = one_verDBList.get(y).UCID_Mros;
            double xANDy = Mros_x.intersectionSizeEstimate(Mros_x,Mros_y);
            if (xANDy >= minSup*delta){
                withPurnCount++;
                VerDB_Mros verDB_xy = ExtendP(VerDB_x,one_verDBList.get(y),minSup);
                if (verDB_xy.cnt >= minSup){
                    List<Integer> item_new = new ArrayList<>(fre_x);
                    item_new.add(y);
                    k_MDBmap.put(item_new,verDB_xy.UCID_Mros);
                    List<Integer> candList_new = itemCadMap.get(y);
                    if (candList_new!=null) GrowP(item_new, verDB_xy, candList_new, minSup, delta);
                } else if (verDB_xy.cnt >= minSup*delta) {
                    List<Integer> item_new = new ArrayList<>(fre_x);
                    item_new.add(y);
                    semi_MDBmap.put(item_new,verDB_xy.UCID_Mros);
                    List<Integer> candList_new = itemCadMap.get(y);
                    if (candList_new!=null) GrowP(item_new, verDB_xy, candList_new, minSup, delta);
                }
            }
        }
    }


    /**
     * ExtendP: sinh VerDB_Mros của pattern <vx, vy> bằng cách giao SID.
     * Cải tiến 2.3.4: two-pointer O(m+n) trên CSR sids[] thay HashMap.get().
     * Cải tiến 2.3.3: kết quả encode gap+varint vào posData[] (không lưu int[] tuyệt đối).
     */
    private static VerDB_Mros ExtendP(VerDB_Mros vx, VerDB_Mros vy, double minSup) throws IOException {
        // Cải tiến 2.3.4: two-pointer trên CSR sids[] phẳng
        // Cải tiến 2.3.3: kết quả lưu dưới dạng gap+varint trong posData[]
        int maxLen = Math.min(vx.cnt, vy.cnt);
        int[]    tmpSids     = new int[maxLen];
        int[]    tmpFirstPid = new int[maxLen];
        byte[][] tmpEncoded  = new byte[maxLen][];
        int      rCnt        = 0;

        int i = 0, j = 0;
        while (i < vx.cnt && j < vy.cnt) {
            int sx = vx.sids[i], sy = vy.sids[j];
            if      (sx < sy) { i++; continue; }
            else if (sx > sy) { j++; continue; }

            // Fast-path: đọc firstPid[] trực tiếp, không decode
            int first_x = vx.firstPid[i];
            int first_y = vy.firstPid[j];

            // Decode pids_y để lấy last_y và lọc
            int[] py     = vy.decodePids(j);
            int   last_y = py[py.length - 1];

            if (first_x >= first_y && first_x >= last_y) { i++; j++; continue; }

            int    fp;
            byte[] enc;
            if (first_x < first_y) {
                // Tất cả py hợp lệ: sao chép encoded bytes của vy trực tiếp
                fp          = first_y;
                int byteLen = vy.off[j + 1] - vy.off[j];
                enc         = new byte[byteLen];
                System.arraycopy(vy.posData, vy.off[j], enc, 0, byteLen);
            } else {
                // Lọc py > first_x rồi encode
                int cnt2 = 0;
                for (int p : py) { if (p > first_x) cnt2++; }
                if (cnt2 == 0) { i++; j++; continue; }
                fp = -1;
                byte[] buf = new byte[cnt2 * 5];
                int boff = 0, prev = -1;
                for (int p : py) {
                    if (p > first_x) {
                        if (fp < 0) { fp = p; prev = p; }
                        else { boff = VerDB_Mros.writeVarint(buf, boff, p - prev); prev = p; }
                    }
                }
                enc = new byte[boff];
                System.arraycopy(buf, 0, enc, 0, boff);
            }

            tmpSids[rCnt]     = sx;
            tmpFirstPid[rCnt] = fp;
            tmpEncoded[rCnt]  = enc;
            rCnt++;
            i++; j++;
        }

        // Build CSR result
        VerDB_Mros result = new VerDB_Mros(true);
        result.cnt      = rCnt;
        result.sids     = java.util.Arrays.copyOf(tmpSids, rCnt);
        result.firstPid = java.util.Arrays.copyOf(tmpFirstPid, rCnt);
        result.off      = new int[rCnt + 1];
        int totalBytes  = 0;
        for (int k = 0; k < rCnt; k++) { result.off[k] = totalBytes; totalBytes += tmpEncoded[k].length; }
        result.off[rCnt] = totalBytes;
        result.posData   = new byte[totalBytes];
        int pos = 0;
        for (int k = 0; k < rCnt; k++) {
            System.arraycopy(tmpEncoded[k], 0, result.posData, pos, tmpEncoded[k].length);
            pos += tmpEncoded[k].length;
        }
        for (int k = 0; k < rCnt; k++) result.UCID_Mros.add(result.sids[k]);
        return result;
    }

    public static void deMFP(String deDB, String deOutPut,
                             double minSupRe, double deltaParam) throws IOException {
        MemoryLogger.getInstance().reset();
        startTime = System.currentTimeMillis();
        Set<Integer> deItems = new HashSet<>();
        int deMaxsid =0;
        deWriter =  new BufferedWriter(new FileWriter(deOutPut));// tạo đối tượng ghi file

        // Đọc file xóa (deDB): cập nhật Mros của các 1-pattern bị ảnh hưởng
        try {
            FileInputStream fin = new FileInputStream(new File(deDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = 0; // chỉ số chuỗi
            int pid = 0; // vị trí item
            // T_temp: theo dõi lần đầu xuất hiện của mỗi item trong mỗi chuỗi
            Map<Integer, Set<Integer>> T_temp = new HashMap<>();
            while ((thisLine = reader.readLine()) != null) {
                if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }
                for (String token : thisLine.split(" ")) {
                    if (token.equals("-1")) {
                        pid++;
                    } else if (token.equals("-2")) {
                        sid++;
                        deMaxsid = sid;
                        pid = 0; // reset sau mỗi chuỗi
                        M_ucid_de.remove(sid); // xóa SID khỏi Mros tổng
                    } else {
                        Integer itemName = Integer.parseInt(token);
                        // Chỉ cập nhật Mros lần đầu item xuất hiện trong chuỗi này
                        if (T_temp.containsKey(itemName)){
                            if (T_temp.get(itemName).contains(sid)){
                                continue;
                            }else {
                                if (k_MDBmap.containsKey(itemName)){
                                    k_MDBmap.get(itemName).remove(sid);
                                } else if (semi_MDBmap.containsKey(itemName)) {
                                    semi_MDBmap.get(itemName).remove(sid);
                                }
                            }
                        }else{
                            Set<Integer> sidSet=new HashSet<>();
                            sidSet.add(sid);
                            T_temp.put(itemName,sidSet);
                            if (k_MDBmap.containsKey(itemName)){
                                k_MDBmap.get(itemName).remove(sid);
                            } else if (semi_MDBmap.containsKey(itemName)) {
                                semi_MDBmap.get(itemName).remove(sid);
                            }
                        }
                    }
                }
            }

            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        // Cập nhật ngưỡng support sau khi xóa chuỗi
        double newminSup = Math.ceil((Maxsid - deMaxsid) * minSupRe);
        int count = 0;
        // Lọc lại các pattern: loại bỏ pattern không còn thường xuyên
        List<List<Integer>> PList = new ArrayList<>();
        Iterator<Entry<List<Integer>, Mros>> iter_1 = k_MDBmap.entrySet().iterator();
        while (iter_1.hasNext()){

            Map.Entry<List<Integer>, Mros> entry =(Entry<List<Integer>,Mros>) iter_1.next();
            List<Integer> itemList = entry.getKey();
            if (itemList.size()==1){
                if (entry.getValue().getItemNum()>=newminSup){
                    PList.add(itemList);
                    count++;
                }
            }else {
                Mros M_x=entry.getValue();
                double sup = M_x.intersectionSizeEstimate(M_x,M_ucid_de);
                if(sup < newminSup){
                    iter_1.remove(); // loại pattern không còn thường xuyên
                }else {
                    count++;
                    PList.add(entry.getKey());
                }
            }

        }


        // Lọc semi_MDBmap tương tự
        Iterator<Entry<List<Integer>, Mros>> iter_2 = semi_MDBmap.entrySet().iterator();
        while (iter_2.hasNext()) {
            Map.Entry<List<Integer>, Mros> entry = iter_2.next();
            Mros M_x=entry.getValue();
            List<Integer> itemList = entry.getKey();
            if (itemList.size()==1){
                if (entry.getValue().getItemNum()>=newminSup){
                    PList.add(itemList);
                    count++;
                }
            }else {
                double sup = M_x.intersectionSizeEstimate(M_x,M_ucid_de);
                if(sup < newminSup){
                    //System.out.println("unfrep: "+ entry.getKey()+"Sup: "+sup);
                    iter_2.remove(); // loại pattern không còn bán thường xuyên
                }else {
                    count++;
                    PList.add(entry.getKey());
                    //System.out.println("newfreP: "+ entry.getKey()+"Sup: "+sup);
                }
            }
        }

        MemoryLogger.getInstance().checkMemory();
        endTime = System.currentTimeMillis();
        //saveDePattern(newfreList);
        saveDePattern(PList);
        System.out.println("New MinSup: "+ newminSup);
        System.out.println("New count: "+count);
        System.out.println("New totalTime: "+(endTime-startTime));
        System.out.println("New Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory());
    }


    public static void addMFP(String addDB,String addOutPut, double minSupRe,double delta) throws IOException{
        MemoryLogger.getInstance().reset();
        int addMaxsid =0;
        deWriter =  new BufferedWriter(new FileWriter(addOutPut));// tạo đối tượng ghi file
        Set<Integer> addItems = new HashSet<>();
        Set<Integer> newfrelist =new HashSet<>();
        startTime = System.currentTimeMillis();


        // BƯỚC 1: Đọc file thêm (addDB), cập nhật addItemMap và one_verDBList
        try {
            FileInputStream fin = new FileInputStream(new File(addDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = Maxsid;// chỉ số SID bắt đầu từ Maxsid (nối tiếp DB gốc)
            int pid = 0;// vị trí item trong chuỗi
            // đọc từng dòng
            while((thisLine = reader.readLine()) != null){
                // bỏ qua dòng trống/comment
                if(thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        addMaxsid = sid;
                        pid = 0;// reset sau mỗi chuỗi
                    } else {
                        Integer itemName = Integer.parseInt(token);

                        // [CẢI TIẾN] buildAdd
                        VerDB_Mros va = addItemMap.get(itemName);
                        if (va == null) { va = new VerDB_Mros(); addItemMap.put(itemName, va); }
                        if (va._curSid != sid) va.UCID_Mros.add(sid);
                        va.buildAdd(sid, pid);

                        VerDB_Mros vo = one_verDBList.get(itemName);
                        if (vo == null) { vo = new VerDB_Mros(); one_verDBList.put(itemName, vo); }
                        if (vo._curSid != sid) vo.UCID_Mros.add(sid);
                        //vo.buildAdd(sid, pid);
                        addItems.add(itemName);
                    }
                }
            }
            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        // [CẢI TIẾN] Finalize các VerDB_Mros mới thêm
        for (VerDB_Mros v : addItemMap.values()) { if (v.sids == null) v.freeze(); }
        for (VerDB_Mros v : one_verDBList.values()) { if (v.sids == null) v.freeze(); }

        double newsup = Math.ceil(minSupRe*addMaxsid);
        Set<Integer> freItemList_temp = new HashSet<>();
        for (Integer x : freItemList){
            if (one_verDBList.get(x).getSupport()>=newsup){
                add_FreCount++;
                freItemList_temp.add(x);
            }
        }
        newfrelist.addAll(addItems);
        newfrelist.removeAll(freItemList_temp);
        for (Integer x : newfrelist){
            if (one_verDBList.get(x).getSupport()>=newsup){
                add_FreCount++;
                freItemList_temp.add(x);
            }
        }

        double threshold = newsup*(1-delta);


        // Sinh và kiểm tra 2-pattern frequent/semi-frequent
        Set<List<Integer>> two_maylist = new HashSet<>();
        Map<List<Integer>, VerDB_Mros> maybeverDB_temp = new HashMap<>();
        for (Integer x : addItems){
            List<Integer> canlist_temp = new ArrayList<>();
            VerDB_Mros verDB_x = addItemMap.get(x);
            for (Integer y : addItems){
                List<Integer> itemList = new ArrayList<>();
                itemList.add(x);
                itemList.add(y);
                VerDB_Mros verDB_y = addItemMap.get(y);
                if (k_MDBmap.containsKey(itemList)){
                    // Pattern đã là frequent — cập nhật Mros với SID mới
                    VerDB_Mros verDB_xy = ExtendP(verDB_x, verDB_y, minSupRe * addMaxsid);
                    for (int k = 0; k < verDB_xy.cnt; k++) k_MDBmap.get(itemList).add(verDB_xy.sids[k]);
                    if (k_MDBmap.get(itemList).getItemNum() >= newsup) {
                        add_FreCount++;
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        addFreList.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                    }
//                    else if (k_MDBmap.get(itemList).getItemNum()>=newsup*delta){
//                        canlist_temp.add(y);
//                        two_maylist.add(itemList);
//                        maybeverDB_temp.put(itemList,verDB_xy);
//                        k_MDBmap.remove(itemList);
//                        semi_MDBmap.put(itemList,verDB_xy.UCID_Mros);
//                    }
                } else if (semi_MDBmap.containsKey(itemList)) {
                    // Pattern đang là semi-frequent — kiểm tra có thăng lên frequent không
                    VerDB_Mros verDB_xy = ExtendP(verDB_x, verDB_y, minSupRe * addMaxsid);
                    for (int k = 0; k < verDB_xy.cnt; k++) semi_MDBmap.get(itemList).add(verDB_xy.sids[k]);
                    if (semi_MDBmap.get(itemList).getItemNum() >= newsup) {
                        add_FreCount++;
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        addFreList.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                        k_MDBmap.put(itemList,semi_MDBmap.get(itemList));
                    }
//                    else if (semi_MDBmap.get(itemList).getItemNum()>=newsup*delta){
//                        canlist_temp.add(y);
//                        two_maylist.add(itemList);
//                        maybeverDB_temp.put(itemList,verDB_xy);
//                        semi_MDBmap.put(itemList,verDB_xy.UCID_Mros);
//                    }
                }
//                else if (verDB_xy.cnt>=threshold){
//                    // Pattern chưa tồn tại trong DB (commented out)
//                    canlist_temp.add(y);
//                    two_maylist.add(itemList);
//                    maybeverDB_temp.put(itemList,verDB_xy);
//                    maybe_MDBmap.put(itemList,verDB_xy.UCID_Mros);
//                }
            }
            itemCad_temp.put(x,canlist_temp);
        }

        for (List<Integer> two_integers : two_maylist){
            Integer last = two_integers.get(two_integers.size()-1);
            List<Integer> canList_1 = itemCad_temp.get(last);
            VerDB_Mros verDBMros_1 = maybeverDB_temp.get(two_integers);
            if (canList_1 != null){
                GrowP_add(two_integers,verDBMros_1,canList_1,newsup,delta);
            }
            //savePattern(prefixVerList,minSup);
        }

        Set<List<Integer>> freList = k_MDBmap.keySet();
        freList.removeAll(addFreList);
        for (List<Integer> x : freList){
            double sup_x =k_MDBmap.get(x).getItemNum();
            if (sup_x>=newsup){
                addFreList.add(x);
            }
//            else if (sup_x>=newsup*delta){
////                k_MDBmap.remove(x);
//                semi_MDBmap.put(x,k_MDBmap.get(x));
//            }
        }
        MemoryLogger.getInstance().checkMemory();
        endTime = System.currentTimeMillis();
        saveDePattern(freItemList);
        saveAddPattern(addFreList);
        System.out.println("New ADD MinSup: "+ newsup);
        System.out.println("New fre count: "+add_FreCount);
        System.out.println("New ADD totalTime: "+(endTime-startTime)+" ms");
        System.out.println("New ADD Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory());
    }

    /**
     * GrowP_add: mở rộng pattern trong pha Insert.
     * Chỉ xử lý pattern đã có trong k_MDBmap hoặc semi_MDBmap.
     */
    public static void GrowP_add(List<Integer> fre_x, VerDB_Mros VerDB_x, List<Integer> candidateList, double newminSup, double delta) throws IOException {
        // [CẢI TIẾN] item_new sau khi check, recurse ngay thay vì tích lũy freVerList_new
        for (Integer y : candidateList) {
            List<Integer> item_new = new ArrayList<>(fre_x);
            item_new.add(y);
            if (k_MDBmap.containsKey(item_new)){
                VerDB_Mros verDB_xy = ExtendP(VerDB_x,addItemMap.get(y),newminSup);
                for (int k=0; k<verDB_xy.cnt; k++) k_MDBmap.get(item_new).add(verDB_xy.sids[k]);
                if (k_MDBmap.get(item_new).getItemNum()>=newminSup){
                    List<Integer> cands = itemCad_temp.get(y);
                    if (cands!=null) GrowP_add(item_new, verDB_xy, cands, newminSup, delta);
                }
            } else if (semi_MDBmap.containsKey(item_new)) {
                VerDB_Mros verDB_xy = ExtendP(VerDB_x,addItemMap.get(y),newminSup);
                for (int k=0; k<verDB_xy.cnt; k++) semi_MDBmap.get(item_new).add(verDB_xy.sids[k]);
                if (semi_MDBmap.get(item_new).getItemNum()>=newminSup){
                    List<Integer> cands = itemCad_temp.get(y);
                    if (cands!=null) GrowP_add(item_new, verDB_xy, cands, newminSup, delta);
                }
            }
        }
    }



    public static void FullyMFP(String addDB,String deDB,String fullyOutPut, double minSupRe,double delta) throws IOException{
        MemoryLogger.getInstance().reset();
        int addMaxsid =0;
        deWriter =  new BufferedWriter(new FileWriter(fullyOutPut));// tạo đối tượng ghi file
        Set<Integer> addItems = new HashSet<>();
        Set<Integer> newfrelist =new HashSet<>();
        startTime = System.currentTimeMillis();


        // BƯỚC 1a: Đọc file thêm (addDB)
        try {
            FileInputStream fin = new FileInputStream(new File(addDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = Maxsid;// chỉ số SID bắt đầu từ Maxsid (nối tiếp DB gốc)
            int pid = 0;// vị trí item trong chuỗi
            // đọc từng dòng
            while((thisLine = reader.readLine()) != null){
                // bỏ qua dòng trống/comment
                if(thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        Maxsid = sid;
                        pid = 0;// reset sau mỗi chuỗi
                        M_ucid_de.add(sid);
                    } else {
                        Integer itemName = Integer.parseInt(token);

                        // [CẢI TIẾN] buildAdd
                        VerDB_Mros va = addItemMap.get(itemName);
                        if (va == null) { va = new VerDB_Mros(); addItemMap.put(itemName, va); }
                        if (va._curSid != sid) va.UCID_Mros.add(sid);
                        va.buildAdd(sid, pid);

                        VerDB_Mros vo = one_verDBList.get(itemName);
                        if (vo == null) { vo = new VerDB_Mros(); one_verDBList.put(itemName, vo); }
                        if (vo._curSid != sid) vo.UCID_Mros.add(sid);
                        //vo.buildAdd(sid, pid);
                        addItems.add(itemName);
                    }
                }
            }
            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        for (VerDB_Mros v : addItemMap.values()) { if (v.sids == null) v.freeze(); }
        for (VerDB_Mros v : one_verDBList.values()) { if (v.sids == null) v.freeze(); }

        int deMaxsid =0;

        // BƯỚC 1b: Đọc file xóa (deDB), cập nhật Mros của pattern bị ảnh hưởng
        try {
            FileInputStream fin = new FileInputStream(new File(deDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = 0;// chỉ số chuỗi
            int pid = 0;// vị trí item trong chuỗi
            // đọc từng dòng
            Map<Integer,Set<Integer>> T_temp = new HashMap<>();
            while((thisLine = reader.readLine()) != null){
                // bỏ qua dòng trống/comment
                if(thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        deMaxsid = sid;
                        pid = 0;// reset sau mỗi chuỗi
                        M_ucid_de.remove(sid);
                    } else {
                        Integer itemName = Integer.parseInt(token);
                        // chỉ cập nhật Mros lần đầu item xuất hiện trong chuỗi này
                        if (T_temp.containsKey(itemName)){
                            if (T_temp.get(itemName).contains(sid)){
                                continue;
                            }else {
                                if (k_MDBmap.containsKey(itemName)){
                                    k_MDBmap.get(itemName).remove(sid);
                                } else if (semi_MDBmap.containsKey(itemName)) {
                                    semi_MDBmap.get(itemName).remove(sid);
                                }
                            }
                        }else{
                            Set<Integer> sidSet=new HashSet<>();
                            sidSet.add(sid);
                            T_temp.put(itemName,sidSet);
                            if (k_MDBmap.containsKey(itemName)){
                                k_MDBmap.get(itemName).remove(sid);
                            } else if (semi_MDBmap.containsKey(itemName)) {
                                semi_MDBmap.get(itemName).remove(sid);
                            }
                        }
                    }
                }
            }

            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        // Cập nhật ngưỡng support sau Insert + Delete
        double newminSup = Math.ceil((Maxsid - deMaxsid) * minSupRe);
        int count =0;
        System.out.println(M_ucid_de.getItemNum());

        Set<Integer> freItemList_temp = new HashSet<>();
        newfrelist.addAll(addItems);
        newfrelist.addAll(freItemList);
        newfrelist.addAll(semiItemList);
        for (Integer x : newfrelist){
            if (one_verDBList.get(x).getSupport()>=newminSup){
                freItemList_temp.add(x);
            }
        }

        double threshold = newminSup*(1-delta);


        // Sinh và kiểm tra 2-pattern frequent/semi-frequent
        Set<List<Integer>> two_maylist = new HashSet<>();
        Map<List<Integer>, VerDB_Mros> maybeverDB_temp = new HashMap<>();
        for (Integer x : addItems){
            List<Integer> canlist_temp = new ArrayList<>();
            VerDB_Mros verDB_x = addItemMap.get(x);
            for (Integer y : addItems){
                List<Integer> itemList = new ArrayList<>();
                itemList.add(x);
                itemList.add(y);
                VerDB_Mros verDB_y = addItemMap.get(y);
                if (k_MDBmap.containsKey(itemList)){
                    // Pattern đã là frequent — cập nhật với SID mới từ addDB
                    VerDB_Mros verDB_xy = ExtendP(verDB_x, verDB_y, minSupRe * addMaxsid);
                    for (int k = 0; k < verDB_xy.cnt; k++) k_MDBmap.get(itemList).add(verDB_xy.sids[k]);
                    Mros M_x = k_MDBmap.get(itemList);
                    double sup_1 = M_x.intersectionSizeEstimate(M_x,M_ucid_de);
                    if (sup_1 >= newminSup){
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        //addFreList.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                    }
//                    else if (k_MDBmap.get(itemList).getItemNum()>=newsup*delta){
//                        canlist_temp.add(y);
//                        two_maylist.add(itemList);
//                        maybeverDB_temp.put(itemList,verDB_xy);
//                        k_MDBmap.remove(itemList);
//                        semi_MDBmap.put(itemList,verDB_xy.UCID_Mros);
//                    }
                } else if (semi_MDBmap.containsKey(itemList)) {
                    // Pattern đang là semi-frequent — kiểm tra sau cập nhật
                    VerDB_Mros verDB_xy = ExtendP(verDB_x, verDB_y, minSupRe * addMaxsid);
                    for (int k = 0; k < verDB_xy.cnt; k++) semi_MDBmap.get(itemList).add(verDB_xy.sids[k]);
                    Mros M_x = semi_MDBmap.get(itemList);
                    double sup_1 = M_x.intersectionSizeEstimate(M_x,M_ucid_de);
                    if (sup_1 >= newminSup){
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        //addFreList.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                        k_MDBmap.put(itemList,semi_MDBmap.get(itemList));
                    }
//                    else if (semi_MDBmap.get(itemList).getItemNum()>=newsup*delta){
//                        canlist_temp.add(y);
//                        two_maylist.add(itemList);
//                        maybeverDB_temp.put(itemList,verDB_xy);
//                        semi_MDBmap.put(itemList,verDB_xy.UCID_Mros);
//                    }
                }
//                else if (verDB_xy.cnt>=threshold){
//                    // Pattern chưa tồn tại trong DB (commented out)
//                    canlist_temp.add(y);
//                    two_maylist.add(itemList);
//                    maybeverDB_temp.put(itemList,verDB_xy);
//                    maybe_MDBmap.put(itemList,verDB_xy.UCID_Mros);
//                }
            }
            itemCad_temp.put(x,canlist_temp);
        }

        for (List<Integer> two_integers : two_maylist){
            Integer last = two_integers.get(two_integers.size()-1);
            List<Integer> canList_1 = itemCad_temp.get(last);
            VerDB_Mros verDBMros_1 = maybeverDB_temp.get(two_integers);
            if (canList_1 != null){
                GrowP_add(two_integers,verDBMros_1,canList_1,newminSup,delta);
            }
            //savePattern(prefixVerList,minSup);
        }

        Set<List<Integer>> freList = k_MDBmap.keySet();
        System.out.println("k_map: "+freList.size());
        Set<List<Integer>> fullyFreList = new HashSet<>();
        for (List<Integer> x : freList){
            Mros M_x = k_MDBmap.get(x);
            double sup_x = M_x.intersectionSizeEstimate(M_x,M_ucid_de);
            if (sup_x>=newminSup){
                fully_FreCount++;
                fullyFreList.add(x);
            }
//            else if (sup_x>=newsup*delta){
////                k_MDBmap.remove(x);
//                semi_MDBmap.put(x,k_MDBmap.get(x));
//            }
        }
        Set<List<Integer>> freList_1 = semi_MDBmap.keySet();
        System.out.println("semi_map: "+freList_1.size());
        for (List<Integer> x : freList_1){
            Mros M_x = semi_MDBmap.get(x);
            double sup_x = M_x.intersectionSizeEstimate(M_x,M_ucid_de);
            if (sup_x>=newminSup){
                fully_FreCount++;
                fullyFreList.add(x);
            }
//            else if (sup_x>=newsup*delta){
////                k_MDBmap.remove(x);
//                semi_MDBmap.put(x,k_MDBmap.get(x));
//            }
        }

        MemoryLogger.getInstance().checkMemory();
        endTime = System.currentTimeMillis();
        //saveDePattern(freItemList);
        saveAddPattern(fullyFreList);
        System.out.println("New fully MinSup: "+ newminSup);
        System.out.println("New fre count: "+fully_FreCount);
        System.out.println("New fully totalTime: "+(endTime-startTime)+" ms");
        System.out.println("New fully Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory());
    }



    /** Ghi các pattern thường xuyên ra file kết quả (dùng cho VerDB_Mros đầy đủ). */
    private void savePattern (HashMap<List,VerDB_Mros> one_verDBListTemp, double minSup) throws IOException {
        StringBuilder r = new StringBuilder("");
        for(Entry<List,VerDB_Mros> entry: one_verDBListTemp.entrySet()){
            /*
             * Ghi tên các item trong k-pattern
             */
            r.append('(');
            List<Integer> itemNames = new ArrayList<>();
            itemNames = entry.getKey();
            r.append("itemName: ");
            for(Integer itemName : itemNames){
                String string = itemName.toString();
                r.append(string);
                r.append(" -1 ");
            }
            r.append(')');
            r.append("\n");

            /*
             * Ghi support của k-pattern
             */
            r.append("#SUP: ");// nhãn support
            r.append(entry.getValue().getSupport());// giá trị support
            r.append("\n");
            patternCount++; // tăng đếm pattern
        }
        r.append(patternCount);
        r.append("\n");
        writer.write(r.toString());
        writer.newLine();
        writer.flush();
    }

    /** Ghi danh sách pattern ra file kết quả sau deMFP. */
    private static void saveDePattern(Set<Integer> Fre) throws IOException {
        StringBuilder dr = new StringBuilder("");
        for(Integer p : Fre){
            // Ghi 1-pattern
            dr.append(p);
            dr.append(" -2");
            /*
             * Ghi support 1-pattern (commented out)
             */
//            dr.append("#SUP: ");// nhãn support
//            dr.append(one_verDBList.get(p).allInfo.size());// giá trị support
            dr.append("\n");
        }
        deWriter.write(dr.toString());
        deWriter.newLine();
        deWriter.flush();
    }
    private static void saveDePattern(List<List<Integer>> deFreList) throws IOException {
        StringBuilder dr = new StringBuilder("");
        for(List<Integer> pList : deFreList){
            for (Integer p : pList){
                /*
                 * Ghi tên item trong 1-pattern
                 */
                dr.append(p);
                dr.append(" -1 ");

            }
            dr.append(" -2");
            /*
             * Ghi support 1-pattern (commented out)
             */
//            dr.append("#SUP: ");// nhãn support
//            dr.append(one_verDBList.get(p).allInfo.size());// giá trị support
            dr.append("\n");
        }
        deWriter.write(dr.toString());
        deWriter.newLine();
        deWriter.flush();
    }

    private static void saveAddPattern(Set<List<Integer>> addFreList) throws IOException {
        StringBuilder dr = new StringBuilder("");
        for(List<Integer> pList : addFreList){
            for (Integer p : pList){
                /*
                 * Ghi tên item trong 1-pattern
                 */
                dr.append(p);
                dr.append(" -1 ");

            }
            dr.append(" -2");
            /*
             * Ghi support 1-pattern (commented out)
             */
//            dr.append("#SUP: ");// nhãn support
//            dr.append(one_verDBList.get(p).allInfo.size());// giá trị support
            dr.append("\n");
        }
        deWriter.write(dr.toString());
        deWriter.newLine();
        deWriter.flush();
    }

    public void printStatistics() {
        StringBuilder r = new StringBuilder(200);
        r.append("=============  De_CSPM v0.23/08/28 - STATISTICS =============\n Total time ~ ");
        r.append(endTime - startTime);
        r.append(" ms\n");
        r.append(" Frequent sequences count : " + patternCount);
        r.append('\n');
        r.append("the number of extension : "+ extension);

        r.append('\n');
        r.append(" the number of purning : "+withPurnCount);
        r.append(" Max memory (mb) : " );
        r.append(MemoryLogger.getInstance().getMaxMemory());
//        r.append(patternCount);
        r.append('\n');
        r.append("minsup " + minSup);
        r.append('\n');
        r.append("=========================================================\n");
        System.out.println(r.toString());
    }



}