package utils;

import java.util.Objects;

public final class PatternEntry {
    public final String key;        
    public Object sidMros;          
    public double supEst;           
    public CsrPosMap posMap;        

    public int entryCount() { return posMap == null ? 0 : posMap.entryCount; }
    public int totalPid() { return posMap == null ? 0 : posMap.totalPid; }

    public PatternEntry(String key) {
        this.key = key;
        this.sidMros = null;
        this.supEst = 0;
        this.posMap = null;
    }

    public boolean isActive() { return posMap != null; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PatternEntry)) return false;
        return Objects.equals(key, ((PatternEntry)o).key);
    }

    @Override public int hashCode() { return Objects.hash(key); }

    @Override public String toString() { return "PatternEntry(" + key + ")"; }
}
