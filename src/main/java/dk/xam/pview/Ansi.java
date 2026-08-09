package dk.xam.pview;

import org.aesh.command.invocation.CommandInvocation;
import org.aesh.terminal.utils.ANSIBuilder;

/** Thin wrappers around aesh ANSIBuilder for inline use. */
public class Ansi {

    public static String cyan(String s)   { return ANSIBuilder.builder().cyanText(s).toString(); }
    public static String green(String s)  { return ANSIBuilder.builder().greenText(s).toString(); }
    public static String yellow(String s) { return ANSIBuilder.builder().yellowText(s).toString(); }
    public static String red(String s)    { return ANSIBuilder.builder().redText(s).toString(); }
    public static String bold(String s)   { return ANSIBuilder.builder().bold().append(s).boldOff().toString(); }
    public static String dim(String s)    { return ANSIBuilder.builder().faint().append(s).faintOff().toString(); }

    public static void println(CommandInvocation inv, String s) {
        inv.println(s);
    }
}
