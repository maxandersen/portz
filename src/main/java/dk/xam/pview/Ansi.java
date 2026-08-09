package dk.xam.pview;

/** ponytail: minimal ANSI helpers, no dependency needed */
public class Ansi {
    private static final String RESET = "\033[0m";

    public static String cyan(String s)   { return "\033[36m" + s + RESET; }
    public static String green(String s)  { return "\033[32m" + s + RESET; }
    public static String yellow(String s) { return "\033[33m" + s + RESET; }
    public static String red(String s)    { return "\033[31m" + s + RESET; }
    public static String bold(String s)   { return "\033[1m" + s + RESET; }
    public static String dim(String s)    { return "\033[2m" + s + RESET; }
}
