import java.io.IOException;

public class mainRunMrosFPM {
    public static void main(String[] args) throws IOException {
        String inputFilePath = "indeDataset/Sign/Sign_original.txt";
        String outputFilePath = "outputTest_improved.txt";
        double minSupRe = 0.5;
        double delta = 0.2;

        algoFpmMros algo = new algoFpmMros();
        algo.ReadFileToVerDB_Mros(inputFilePath, outputFilePath, minSupRe, 1 - delta);

        System.out.println("====== Fully Dynamic ======");
        String inFilePath  = "indeDataset/Sign/Sign_11_2.txt";
        String deFilePath  = "indeDataset/Sign/Sign_01_1.txt";
        String fullyOutPath = "indeDataset/testBible_improved.txt";
        algoFpmMros.FullyMFP(inFilePath, deFilePath, fullyOutPath, minSupRe, 1 - delta);
    }
}