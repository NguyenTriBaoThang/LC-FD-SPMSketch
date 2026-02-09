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
import utils.GapVarintPosList;

public class algoFpmMros {
    private static long startTime;
    private static long endTime;
    static int patternCount =0;
    static int add_FreCount = 0;
    static int fully_FreCount=0;
    static int withPurnCount= 0;
    static int extension = 0;

    public void runAlgorithm(String inputFilePath,
                             String outputFilePath,
                             double minSupRe,
                             double delta,
                             String addFilePath,
                             String deFilePath) throws IOException {

        double deltaFactor = 1.0 - delta;

        boolean hasAdd = !isBlank(addFilePath);
        boolean hasDe  = !isBlank(deFilePath);

        if (!hasAdd && !hasDe) {
            ReadFileToVerDB_Mros(inputFilePath, outputFilePath, minSupRe, deltaFactor);
        } else {
            FullyMFP(addFilePath, deFilePath, outputFilePath, minSupRe, deltaFactor);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    static Map<Integer,VerDB_Mros> one_verDBList = new HashMap<>();
    static Map<Integer,VerDB_Mros> addItemMap = new HashMap<>();

    static Map<List<Integer>,Mros> k_MDBmap = new HashMap<>();

    static Map<List<Integer>,Mros> semi_MDBmap = new HashMap<>();

    static Map<List<Integer>,Mros> maybe_MDBmap = new HashMap<>();

    static double minSup =0;
    static int Maxsid = 0;

    static Mros M_ucid_de = new Mros(10,16,0.38,8);

    BufferedWriter writer = null;
    static BufferedWriter deWriter = null;

    static Set<Integer> freItemList = new HashSet<>();
    static Set<List<Integer>> addFreList = new HashSet<>();

    static Set<Integer> semiItemList = new HashSet<>();

    static Set<Integer> unFreList =new HashSet<>();
    static double delta = 0;

    static List<List<Integer>> two_freItemList = new ArrayList<>();

    static Map<Integer,List<Integer>> itemCadMap = new HashMap<>();
    static Map<Integer, List<Integer>> itemCad_temp = new HashMap<>();

    public void ReadFileToVerDB_Mros(String input,String outputFilePath,double minSupRe,double delta) throws IOException {
        writer =  new BufferedWriter(new FileWriter(outputFilePath));
        MemoryLogger.getInstance().reset();
        int pidsum =0;
        double variance=0;

        try {
            FileInputStream fin = new FileInputStream(new File(input));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = 0;
            int pid = 0;
            double subvar=0;

            while((thisLine = reader.readLine()) != null){
                if(thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        Maxsid = sid;
                        pidsum+=pid;
                        subvar=(pid-51.997)*(pid-51.997);
                        variance+=subvar;
                        pid = 0;
                        M_ucid_de.add(sid);
                    } else {
                        Integer itemName = Integer.parseInt(token);

                        if (!one_verDBList.containsKey(itemName)) {
                            VerDB_Mros itemInformation = new VerDB_Mros();
                            itemInformation.UCID_Mros.add(sid);

                            GapVarintPosList posList = new GapVarintPosList();
                            posList.add(pid);
                            itemInformation.allInfo.put(sid,posList);

                            one_verDBList.put(itemName,itemInformation);
                        } else {
                            VerDB_Mros verDB_r = one_verDBList.get(itemName);
                            if (verDB_r.allInfo.containsKey(sid)){
                                verDB_r.allInfo.get(sid).add(pid);
                            } else {
                                verDB_r.UCID_Mros.add(sid);
                                GapVarintPosList posList = new GapVarintPosList();
                                posList.add(pid);
                                verDB_r.allInfo.put(sid,posList);
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("variance: "+variance);

        minSup = minSupRe * Maxsid;
        if (minSup == 0) {
            minSup = 1;
        }

        Iterator<Entry<Integer, VerDB_Mros>> iter = one_verDBList.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<Integer, VerDB_Mros> entry = iter.next();
            if(entry.getValue().getSupport() >= minSup){
                freItemList.add(entry.getKey());
                List<Integer> p = new ArrayList<>();
                p.add(entry.getKey());
                k_MDBmap.put(p,entry.getValue().UCID_Mros);
                patternCount++;
            } else if (entry.getValue().getSupport() >= minSup*delta) {
                List<Integer> p = new ArrayList<>();
                p.add(entry.getKey());
                semi_MDBmap.put(p,entry.getValue().UCID_Mros);
                semiItemList.add(entry.getKey());
            } else {
                unFreList.add(entry.getKey());
            }
        }

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

                if (verDBMros_xy.allInfo.size()>=minSup){
                    two_freItemList.add(itemList);
                    k_verDB_temp.put(itemList,verDBMros_xy);
                    k_MDBmap.put(itemList,verDBMros_xy.UCID_Mros);
                    candlist.add(integer2);
                }else if (verDBMros_xy.allInfo.size()>=minSup*delta){
                    semi_twolist.add(itemList);
                    semi_verDB_temp.put(itemList,verDBMros_xy);
                    semi_MDBmap.put(itemList,verDBMros_xy.UCID_Mros);
                    candlist.add(integer2);
                }
            }
            itemCadMap.put(integer1,candlist);
        }

        /**
         * 频繁的二模式模式增长 (DFS)
         */
        for (List<Integer> two_integers : two_freItemList){
            Integer last = two_integers.get(two_integers.size()-1);
            List<Integer> canList_1 = itemCadMap.get(last);
            VerDB_Mros verDBMros_1 = k_verDB_temp.get(two_integers);
            if (canList_1 != null && verDBMros_1 != null){
                GrowP(two_integers,verDBMros_1,canList_1,minSup,delta);
            }
        }

        for (List<Integer> semiPtwo : semi_twolist){
            Integer last = semiPtwo.get(semiPtwo.size()-1);
            List<Integer> canList_1 = itemCadMap.get(last);
            VerDB_Mros verDBMros_1 = semi_verDB_temp.get(semiPtwo);
            if (canList_1!=null && verDBMros_1 != null){
                GrowP(semiPtwo,verDBMros_1,canList_1,minSup,delta);
            }
        }
    }

    public static void GrowP(List<Integer> fre_x,VerDB_Mros VerDB_x,List<Integer> candidateList,double minSup,double delta) throws IOException{
        Mros Mros_x = VerDB_x.UCID_Mros;

        for (Integer y : candidateList) {
            Mros Mros_y = one_verDBList.get(y).UCID_Mros;
            double xANDy = Mros.intersectionSizeEstimate(Mros_x, Mros_y);

            if (xANDy >= minSup*delta){
                withPurnCount++;

                VerDB_Mros verDB_xy = ExtendP(VerDB_x,one_verDBList.get(y),minSup);
                int sup = verDB_xy.allInfo.size();

                if (sup>=minSup || sup>=minSup*delta) {
                    List<Integer> item_new = new ArrayList<>();
                    item_new.addAll(fre_x);
                    item_new.add(y);

                    if (sup>=minSup){
                        k_MDBmap.put(item_new,verDB_xy.UCID_Mros);
                    } else {
                        semi_MDBmap.put(item_new,verDB_xy.UCID_Mros);
                    }

                    Integer last = y;
                    List<Integer> candList_new = itemCadMap.get(last);
                    if (candList_new!=null) {
                        GrowP(item_new, verDB_xy, candList_new, minSup, delta);
                    }
                    // 回来后，verDB_xy 会尽快被GC（降低峰值内存）
                }
            }
        }
    }

    private static VerDB_Mros ExtendP(VerDB_Mros VerDB_x,VerDB_Mros VerDB_y,double minSup) throws IOException {

        Map<Integer, GapVarintPosList> posMap_x = VerDB_x.allInfo;
        Map<Integer, GapVarintPosList> posMap_y = VerDB_y.allInfo;

        VerDB_Mros verDB_temp = new VerDB_Mros();

        for (Integer ucid : posMap_x.keySet()) {
            GapVarintPosList yList = posMap_y.get(ucid);
            if (yList == null) continue;

            GapVarintPosList xList = posMap_x.get(ucid);

            int first_x = xList.first();
            int first_y = yList.first();
            int last_y  = yList.last();

            GapVarintPosList posList_temp = null;

            if (first_x < first_y) {
                // copy all y
                posList_temp = yList.copyFilteredGreaterThan(-1);
            } else if (first_x < last_y) {
                // keep y positions > first_x
                posList_temp = yList.copyFilteredGreaterThan(first_x);
            }

            if (posList_temp != null && !posList_temp.isEmpty()) {
                verDB_temp.UCID_Mros.add(ucid);
                verDB_temp.allInfo.put(ucid, posList_temp);
            }
        }
        return verDB_temp;
    }

    // ====================== deMFP ======================
    public static void deMFP(String deDB,String deOutPut, double minSupRe,double delta) throws IOException{
        MemoryLogger.getInstance().reset();
        startTime = System.currentTimeMillis();
        int deMaxsid =0;
        deWriter =  new BufferedWriter(new FileWriter(deOutPut));

        try {
            FileInputStream fin = new FileInputStream(new File(deDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = 0;
            int pid = 0;

            Map<Integer,Set<Integer>> T_temp = new HashMap<>();
            while((thisLine = reader.readLine()) != null){
                if(thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        deMaxsid = sid;
                        pid = 0;
                        M_ucid_de.remove(sid);
                    } else {
                        Integer itemName = Integer.parseInt(token);

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

        double newminSup = Math.ceil((Maxsid-deMaxsid)*minSupRe);
        int count =0;

        List<List<Integer>>PList = new ArrayList<>();
        Iterator<Entry<List<Integer>, Mros>> iter_1 = k_MDBmap.entrySet().iterator();
        while (iter_1.hasNext()){
            Map.Entry<List<Integer>, Mros> entry = iter_1.next();
            List<Integer> itemList = entry.getKey();
            if (itemList.size()==1){
                if (entry.getValue().getItemNum()>=newminSup){
                    PList.add(itemList);
                    count++;
                }
            }else {
                Mros M_x=entry.getValue();
                double sup = Mros.intersectionSizeEstimate(M_x,M_ucid_de);
                if(sup < newminSup){
                    iter_1.remove();
                }else {
                    count++;
                    PList.add(entry.getKey());
                }
            }
        }

        Iterator<Entry<List<Integer>, Mros>> iter_2 = semi_MDBmap.entrySet().iterator();
        while (iter_2.hasNext()){
            Map.Entry<List<Integer>, Mros> entry = iter_2.next();
            Mros M_x=entry.getValue();
            List<Integer> itemList = entry.getKey();
            if (itemList.size()==1){
                if (entry.getValue().getItemNum()>=newminSup){
                    PList.add(itemList);
                    count++;
                }
            }else {
                double sup = Mros.intersectionSizeEstimate(M_x,M_ucid_de);
                if(sup < newminSup){
                    iter_2.remove();
                }else {
                    count++;
                    PList.add(itemList);
                }
            }
        }

        MemoryLogger.getInstance().checkMemory();
        endTime = System.currentTimeMillis();
        saveDePattern(PList);
        System.out.println("New MinSup: "+ newminSup);
        System.out.println("New count: "+count);
        System.out.println("New totalTime: "+(endTime-startTime));
        System.out.println("New Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory());
    }

    // ====================== addMFP ======================
    public static void addMFP(String addDB,String addOutPut, double minSupRe,double delta) throws IOException{
        MemoryLogger.getInstance().reset();
        int addMaxsid =0;
        deWriter =  new BufferedWriter(new FileWriter(addOutPut));
        Set<Integer> addItems = new HashSet<>();
        Set<Integer> newfrelist =new HashSet<>();
        startTime = System.currentTimeMillis();

        try {
            FileInputStream fin = new FileInputStream(new File(addDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = Maxsid;
            int pid = 0;

            while((thisLine = reader.readLine()) != null){
                if(thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        addMaxsid = sid;
                        pid = 0;
                    } else {
                        Integer itemName = Integer.parseInt(token);

                        if (addItemMap.containsKey(itemName)){
                            if (addItemMap.get(itemName).allInfo.containsKey(sid)){
                                addItemMap.get(itemName).allInfo.get(sid).add(pid);
                            }else {
                                GapVarintPosList posList = new GapVarintPosList();
                                posList.add(pid);
                                addItemMap.get(itemName).UCID_Mros.add(sid);
                                addItemMap.get(itemName).allInfo.put(sid,posList);
                            }
                        }else {
                            VerDB_Mros verDB = new VerDB_Mros();
                            verDB.UCID_Mros.add(sid);
                            GapVarintPosList posList = new GapVarintPosList();
                            posList.add(pid);
                            verDB.allInfo.put(sid,posList);
                            addItemMap.put(itemName,verDB);
                        }

                        if (one_verDBList.containsKey(itemName)){
                            if (one_verDBList.get(itemName).allInfo.containsKey(sid)){
                                one_verDBList.get(itemName).allInfo.get(sid).add(pid);
                            }else {
                                GapVarintPosList posList = new GapVarintPosList();
                                posList.add(pid);
                                one_verDBList.get(itemName).UCID_Mros.add(sid);
                                one_verDBList.get(itemName).allInfo.put(sid,posList);
                            }
                        }else {
                            VerDB_Mros verDB = new VerDB_Mros();
                            verDB.UCID_Mros.add(sid);
                            GapVarintPosList posList = new GapVarintPosList();
                            posList.add(pid);
                            verDB.allInfo.put(sid,posList);
                            one_verDBList.put(itemName,verDB);
                        }

                        addItems.add(itemName);
                    }
                }
            }
            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }

        double newsup = Math.ceil(minSupRe*addMaxsid);
        Set<Integer> freItemList_temp = new HashSet<>();
        for (Integer x : freItemList){
            if (one_verDBList.get(x).allInfo.size()>=newsup){
                add_FreCount++;
                freItemList_temp.add(x);
            }
        }
        newfrelist.addAll(addItems);
        newfrelist.removeAll(freItemList_temp);
        for (Integer x : newfrelist){
            if (one_verDBList.get(x).allInfo.size()>=newsup){
                add_FreCount++;
                freItemList_temp.add(x);
            }
        }

        double threshold = newsup*(1-delta);

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
                    VerDB_Mros verDB_xy = ExtendP(verDB_x,verDB_y,minSupRe*addMaxsid);
                    for (Integer ucid : verDB_xy.allInfo.keySet()){
                        k_MDBmap.get(itemList).add(ucid);
                    }
                    if (k_MDBmap.get(itemList).getItemNum()>=newsup){
                        add_FreCount++;
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        addFreList.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                    }
                } else if (semi_MDBmap.containsKey(itemList)) {
                    VerDB_Mros verDB_xy = ExtendP(verDB_x,verDB_y,minSupRe*addMaxsid);
                    for (Integer ucid : verDB_xy.allInfo.keySet()){
                        semi_MDBmap.get(itemList).add(ucid);
                    }
                    if (semi_MDBmap.get(itemList).getItemNum()>=newsup){
                        add_FreCount++;
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        addFreList.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                        k_MDBmap.put(itemList,semi_MDBmap.get(itemList));
                    }
                }
            }
            itemCad_temp.put(x,canlist_temp);
        }

        for (List<Integer> two_integers : two_maylist){
            Integer last = two_integers.get(two_integers.size()-1);
            List<Integer> canList_1 = itemCad_temp.get(last);
            VerDB_Mros verDBMros_1 = maybeverDB_temp.get(two_integers);
            if (canList_1 != null && verDBMros_1 != null){
                GrowP_add(two_integers,verDBMros_1,canList_1,newsup,delta);
            }
        }

        Set<List<Integer>> freList = k_MDBmap.keySet();
        freList.removeAll(addFreList);
        for (List<Integer> x : freList){
            double sup_x =k_MDBmap.get(x).getItemNum();
            if (sup_x>=newsup){
                addFreList.add(x);
            }
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

    public static void GrowP_add(List<Integer> fre_x,VerDB_Mros VerDB_x,List<Integer> candidateList,double newminSup,double delta) throws IOException{
        Map<List<Integer>,VerDB_Mros> freVerList_new =new HashMap<>();
        for (Integer y : candidateList) {
            List<Integer> item_new = new ArrayList<>();
            item_new.addAll(fre_x);
            item_new.add(y);

            if (k_MDBmap.containsKey(item_new)){
                VerDB_Mros verDB_xy = ExtendP(VerDB_x,addItemMap.get(y),newminSup);
                for (Integer ucid : verDB_xy.allInfo.keySet()){
                    k_MDBmap.get(item_new).add(ucid);
                }
                if (k_MDBmap.get(item_new).getItemNum()>=newminSup){
                    freVerList_new.put(item_new,verDB_xy);
                }
            } else if (semi_MDBmap.containsKey(item_new)) {
                VerDB_Mros verDB_xy = ExtendP(VerDB_x,addItemMap.get(y),newminSup);
                for (Integer ucid : verDB_xy.allInfo.keySet()){
                    semi_MDBmap.get(item_new).add(ucid);
                }
                if (semi_MDBmap.get(item_new).getItemNum()>=newminSup){
                    freVerList_new.put(item_new,verDB_xy);
                }
            }
        }
        for (Entry<List<Integer>,VerDB_Mros> entry : freVerList_new.entrySet()){
            Integer last = entry.getKey().get(entry.getKey().size()-1);
            List<Integer> candList_new = itemCad_temp.get(last);
            if (candList_new!=null) {
                GrowP_add(entry.getKey(), entry.getValue(), candList_new, newminSup, delta);
            }
        }
    }

    // ====================== FullyMFP ======================
    public static void FullyMFP(String addDB,String deDB,String fullyOutPut, double minSupRe,double delta) throws IOException{
        MemoryLogger.getInstance().reset();
        int addMaxsid =0;
        deWriter =  new BufferedWriter(new FileWriter(fullyOutPut));
        Set<Integer> addItems = new HashSet<>();
        Set<Integer> newfrelist =new HashSet<>();
        startTime = System.currentTimeMillis();

        try {
            FileInputStream fin = new FileInputStream(new File(addDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = Maxsid;
            int pid = 0;

            while((thisLine = reader.readLine()) != null){
                if(thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        Maxsid = sid;
                        pid = 0;
                        M_ucid_de.add(sid);
                    } else {
                        Integer itemName = Integer.parseInt(token);

                        if (addItemMap.containsKey(itemName)){
                            if (addItemMap.get(itemName).allInfo.containsKey(sid)){
                                addItemMap.get(itemName).allInfo.get(sid).add(pid);
                            }else {
                                GapVarintPosList posList = new GapVarintPosList();
                                posList.add(pid);
                                addItemMap.get(itemName).UCID_Mros.add(sid);
                                addItemMap.get(itemName).allInfo.put(sid,posList);
                            }
                        }else {
                            VerDB_Mros verDB = new VerDB_Mros();
                            verDB.UCID_Mros.add(sid);
                            GapVarintPosList posList = new GapVarintPosList();
                            posList.add(pid);
                            verDB.allInfo.put(sid,posList);
                            addItemMap.put(itemName,verDB);
                        }

                        if (one_verDBList.containsKey(itemName)){
                            if (one_verDBList.get(itemName).allInfo.containsKey(sid)){
                                one_verDBList.get(itemName).allInfo.get(sid).add(pid);
                            }else {
                                GapVarintPosList posList = new GapVarintPosList();
                                posList.add(pid);
                                one_verDBList.get(itemName).UCID_Mros.add(sid);
                                one_verDBList.get(itemName).allInfo.put(sid,posList);
                            }
                        }else {
                            VerDB_Mros verDB = new VerDB_Mros();
                            verDB.UCID_Mros.add(sid);
                            GapVarintPosList posList = new GapVarintPosList();
                            posList.add(pid);
                            verDB.allInfo.put(sid,posList);
                            one_verDBList.put(itemName,verDB);
                        }
                        addItems.add(itemName);
                    }
                }
            }
            reader.close();
        } catch (Exception e){
            e.printStackTrace();
        }

        int deMaxsid =0;

        try {
            FileInputStream fin = new FileInputStream(new File(deDB));
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String thisLine;

            int sid = 0;
            int pid = 0;

            Map<Integer,Set<Integer>> T_temp = new HashMap<>();
            while((thisLine = reader.readLine()) != null){
                if(thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@'){
                    continue;
                }
                for(String token : thisLine.split(" ")){
                    if(token.equals("-1")){
                        pid++;
                    } else if (token.equals("-2")){
                        sid++;
                        deMaxsid = sid;
                        pid = 0;
                        M_ucid_de.remove(sid);
                    } else {
                        Integer itemName = Integer.parseInt(token);

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

        double newminSup = Math.ceil((Maxsid-deMaxsid)*minSupRe);
        System.out.println(M_ucid_de.getItemNum());

        Set<Integer> freItemList_temp = new HashSet<>();
        newfrelist.addAll(addItems);
        newfrelist.addAll(freItemList);
        newfrelist.addAll(semiItemList);
        for (Integer x : newfrelist){
            if (one_verDBList.get(x).allInfo.size()>=newminSup){
                freItemList_temp.add(x);
            }
        }

        double threshold = newminSup*(1-delta);

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
                    VerDB_Mros verDB_xy = ExtendP(verDB_x,verDB_y,minSupRe*addMaxsid);
                    for (Integer ucid : verDB_xy.allInfo.keySet()){
                        k_MDBmap.get(itemList).add(ucid);
                    }
                    Mros M_x = k_MDBmap.get(itemList);
                    double sup_1 = Mros.intersectionSizeEstimate(M_x,M_ucid_de);
                    if (sup_1 >= newminSup){
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                    }
                } else if (semi_MDBmap.containsKey(itemList)) {
                    VerDB_Mros verDB_xy = ExtendP(verDB_x,verDB_y,minSupRe*addMaxsid);
                    for (Integer ucid : verDB_xy.allInfo.keySet()){
                        semi_MDBmap.get(itemList).add(ucid);
                    }
                    Mros M_x = semi_MDBmap.get(itemList);
                    double sup_1 = Mros.intersectionSizeEstimate(M_x,M_ucid_de);
                    if (sup_1 >= newminSup){
                        canlist_temp.add(y);
                        two_maylist.add(itemList);
                        maybeverDB_temp.put(itemList,verDB_xy);
                        k_MDBmap.put(itemList,semi_MDBmap.get(itemList));
                    }
                }
            }
            itemCad_temp.put(x,canlist_temp);
        }

        for (List<Integer> two_integers : two_maylist){
            Integer last = two_integers.get(two_integers.size()-1);
            List<Integer> canList_1 = itemCad_temp.get(last);
            VerDB_Mros verDBMros_1 = maybeverDB_temp.get(two_integers);
            if (canList_1 != null && verDBMros_1 != null){
                GrowP_add(two_integers,verDBMros_1,canList_1,newminSup,delta);
            }
        }

        Set<List<Integer>> freList = k_MDBmap.keySet();
        System.out.println("k_map: "+freList.size());
        Set<List<Integer>> fullyFreList = new HashSet<>();
        for (List<Integer> x : freList){
            Mros M_x = k_MDBmap.get(x);
            double sup_x = Mros.intersectionSizeEstimate(M_x,M_ucid_de);
            if (sup_x>=newminSup){
                fully_FreCount++;
                fullyFreList.add(x);
            }
        }
        Set<List<Integer>> freList_1 = semi_MDBmap.keySet();
        System.out.println("semi_map: "+freList_1.size());
        for (List<Integer> x : freList_1){
            Mros M_x = semi_MDBmap.get(x);
            double sup_x = Mros.intersectionSizeEstimate(M_x,M_ucid_de);
            if (sup_x>=newminSup){
                fully_FreCount++;
                fullyFreList.add(x);
            }
        }

        MemoryLogger.getInstance().checkMemory();
        endTime = System.currentTimeMillis();
        saveAddPattern(fullyFreList);
        System.out.println("New fully MinSup: "+ newminSup);
        System.out.println("New fre count: "+fully_FreCount);
        System.out.println("New fully totalTime: "+(endTime-startTime)+" ms");
        System.out.println("New fully Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory());
    }

    private void savePattern (HashMap<List,VerDB_Mros> one_verDBListTemp, double minSup) throws IOException {
        StringBuilder r = new StringBuilder("");
        for(Entry<List,VerDB_Mros> entry: one_verDBListTemp.entrySet()){
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

            r.append("#SUP: ");
            r.append(entry.getValue().getSupport());
            r.append("\n");
            patternCount++;
        }
        r.append(patternCount);
        r.append("\n");
        writer.write(r.toString());
        writer.newLine();
        writer.flush();
    }

    private static void saveDePattern(Set<Integer> Fre) throws IOException {
        StringBuilder dr = new StringBuilder("");
        for(Integer p : Fre){
            dr.append(p);
            dr.append(" -2");
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
                dr.append(p);
                dr.append(" -1 ");
            }
            dr.append(" -2");
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
                dr.append(p);
                dr.append(" -1 ");
            }
            dr.append(" -2");
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
        r.append('\n');
        r.append("minsup " + minSup);
        r.append('\n');
        r.append("=========================================================\n");
        System.out.println(r.toString());
    }
}