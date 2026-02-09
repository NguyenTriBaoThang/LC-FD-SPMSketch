import java.util.HashMap;
import java.util.Map;
import utils.GapVarintPosList;

public class VerDB_Mros {
    Mros UCID_Mros = new Mros(10,16,0.38,8);

    // BEFORE: Map<Integer, List<Integer>> allInfo
    // AFTER:  Map<Integer, GapVarintPosList> allInfo (compressed)
    Map<Integer, GapVarintPosList> allInfo = new HashMap<>();

    public int getSupport(){
        return allInfo.size();
    }
}
