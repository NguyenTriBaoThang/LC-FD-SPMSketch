package utils;

import java.util.Objects;

/**
 * PatternEntry = pattern metadata in buffer.
 * Lazy PosMap: posMap == null unless pattern is active (used for extension).
 */
public final class PatternEntry {
    public final String key;        // pattern key, e.g. "a" or "a,b" (adapt to your existing type)
    public Object sidMros;          // reuse your Mros class type (keep Object here, replace with your actual Mros)
    public double supEst;           // estimated support (cached)
    public CsrPosMap posMap;        // active only; null for support-only

    // Memory stats for this pattern's posMap
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
