import java.io.IOException;
import utils.MemoryLogger;

public class mainRunMrosFPM {

    // ========== Toggle chạy các chế độ ==========
    private static final boolean RUN_INIT_MINE  = true;   // ReadFileToVerDB_Mros
    private static final boolean RUN_ADD_MINE   = false;  // addMFP
    private static final boolean RUN_DE_MINE    = false;  // deMFP
    private static final boolean RUN_FULLY_MINE = true;   // FullyMFP

    public static void main(String[] args) throws IOException {

        // ================== CHỌN CẤU HÌNH ==================
        // (1) FAST: chạy nhanh hơn, ít pattern hơn
        // double minSupRe = 0.45;
        // double delta = 0.30;

        // (2) REPORT: cân bằng (dùng viết báo cáo)
        double minSupRe = 0.10;
        double delta = 0.40;

        // Trong code đang truyền (1 - delta) vào một số hàm
        double deltaFactor = 1.0 - delta;

        // ================== PATH FILE ==================
        // Init DB
        String inputFilePath  = "indeDataset/Sign/Sign_original.txt";
        String outputFilePath = "outputTest.txt";

        // Add/De/Fully DB
        String addFilePath  = "indeDataset/Sign/Sign_11_2.txt";
        String deFilePath   = "indeDataset/Sign/Sign_01_1.txt";
        String fullyOutPath = "indeDataset/testSign204.txt";

        // ================== RUN ==================
        algoFpmMros algo = new algoFpmMros();

        System.out.println("========= CONFIG =========");
        System.out.println("minSupRe      = " + minSupRe);
        System.out.println("delta         = " + delta);
        System.out.println("deltaFactor   = " + deltaFactor + " (passed into methods)");
        System.out.println("==========================\n");

        // ---- (A) Init mining ----
        if (RUN_INIT_MINE) {
            resetCountersOnly();
            MemoryLogger.getInstance().reset();

            System.out.println("********** START INIT MINING **********");
            long t0 = System.currentTimeMillis();

            algo.ReadFileToVerDB_Mros(inputFilePath, outputFilePath, minSupRe, deltaFactor);

            long t1 = System.currentTimeMillis();
            long runtime = (t1 - t0);

            System.out.println("INIT MINING TIME (ms): " + runtime);
            printStats("INIT", runtime);
            System.out.println("*********** END INIT MINING ***********\n");
        }

        // ---- (B) Add mining ----
        if (RUN_ADD_MINE) {
            resetCountersOnly();
            MemoryLogger.getInstance().reset();

            System.out.println("********** START ADD MINING **********");
            String addOutPath = "outputAdd.txt";
            long t0 = System.currentTimeMillis();

            algoFpmMros.addMFP(addFilePath, addOutPath, minSupRe, deltaFactor);

            long t1 = System.currentTimeMillis();
            long runtime = (t1 - t0);

            System.out.println("ADD MINING TIME (ms): " + runtime);
            printStats("ADD", runtime);
            System.out.println("*********** END ADD MINING ***********\n");
        }

        // ---- (C) De mining ----
        if (RUN_DE_MINE) {
            resetCountersOnly();
            MemoryLogger.getInstance().reset();

            System.out.println("********** START DE MINING **********");
            String deOutPath = "outputDe.txt";
            long t0 = System.currentTimeMillis();

            algoFpmMros.deMFP(deFilePath, deOutPath, minSupRe, delta);

            long t1 = System.currentTimeMillis();
            long runtime = (t1 - t0);

            System.out.println("DE MINING TIME (ms): " + runtime);
            printStats("DE", runtime);
            System.out.println("*********** END DE MINING ***********\n");
        }

        // ---- (D) Fully dynamic mining ----
        if (RUN_FULLY_MINE) {
            resetCountersOnly();
            MemoryLogger.getInstance().reset();

            System.out.println("********** START FULLY DYNAMIC MINING **********");
            long t0 = System.currentTimeMillis();

            algoFpmMros.FullyMFP(addFilePath, deFilePath, fullyOutPath, minSupRe, deltaFactor);

            long t1 = System.currentTimeMillis();
            long runtime = (t1 - t0);

            System.out.println("FULLY DYNAMIC MINING TIME (ms): " + runtime);
            printStats("FULLY", runtime);
            System.out.println("*********** END FULLY DYNAMIC MINING ***********\n");
        }

        System.out.println("DONE.");
    }

    /**
     * In ra các chỉ số cần để đổ vào bảng NCKH:
     * - Maxsid, minSup thực tế
     * - patternCount, extension, prunedExtension
     * - peak memory (MB)
     */
    private static void printStats(String mode, long runtimeMs) {
        double peakMb = MemoryLogger.getInstance().getMaxMemory();

        System.out.println("===== STATISTICS (" + mode + ") =====");
        System.out.println("runtime_ms            = " + runtimeMs);
        System.out.println("Maxsid (|DB|)          = " + algoFpmMros.Maxsid);
        System.out.println("minSup_abs             = " + algoFpmMros.minSup);
        System.out.println("patternCount           = " + algoFpmMros.patternCount);
        System.out.println("extension              = " + algoFpmMros.extension);
        System.out.println("prunedExtension        = " + algoFpmMros.withPurnCount);
        System.out.println("peak_memory_mb         = " + peakMb);
        System.out.println("===============================");
    }

    /**
     * Reset chỉ các biến đếm để mỗi MODE ra số liệu rõ ràng.
     * (Không reset DB structures ở đây vì FULLY cần state sau INIT nếu chạy liên tiếp theo pipeline)
     */
    private static void resetCountersOnly() {
        algoFpmMros.patternCount = 0;
        algoFpmMros.add_FreCount = 0;
        algoFpmMros.fully_FreCount = 0;
        algoFpmMros.withPurnCount = 0;
        algoFpmMros.extension = 0;
    }
}