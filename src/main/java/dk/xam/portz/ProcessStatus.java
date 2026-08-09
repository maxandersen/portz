package dk.xam.portz;

public enum ProcessStatus {
    HEALTHY("●", "\033[32m"),
    ORPHANED("◐", "\033[33m"),
    ZOMBIE("✕", "\033[31m");

    private final String symbol;
    private final String color;

    ProcessStatus(String symbol, String color) {
        this.symbol = symbol;
        this.color = color;
    }

    public String symbol() { return color + symbol + "\033[0m"; }
    public String rawSymbol() { return symbol; }
}
