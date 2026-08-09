package dk.xam.pview;

import dev.tamboui.text.Line;
import dev.tamboui.text.MarkupParser;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.terminal.AnsiStringBuilder;
import org.aesh.command.invocation.CommandInvocation;

/**
 * Markup-based styled text using tamboui's BBCode-style parser.
 * Usage: Ansi.markup("[cyan]hello[/] [bold red]world[/]")
 */
public class Ansi {

    /** Parse tamboui markup to ANSI string. */
    public static String markup(String s) {
        return textToAnsi(MarkupParser.parse(s));
    }

    // Convenience shortcuts
    public static String cyan(String s)   { return markup("[cyan]" + s + "[/]"); }
    public static String green(String s)  { return markup("[green]" + s + "[/]"); }
    public static String yellow(String s) { return markup("[yellow]" + s + "[/]"); }
    public static String red(String s)    { return markup("[red]" + s + "[/]"); }
    public static String bold(String s)   { return markup("[bold]" + s + "[/]"); }
    public static String dim(String s)    { return markup("[dim]" + s + "[/]"); }

    public static void println(CommandInvocation inv, String s) {
        inv.println(s);
    }

    /** Render tamboui Text (with styled spans) to an ANSI escape string. */
    private static String textToAnsi(Text text) {
        var sb = new StringBuilder();
        for (int i = 0; i < text.lines().size(); i++) {
            if (i > 0) sb.append('\n');
            Line line = text.lines().get(i);
            for (Span span : line.spans()) {
                var style = span.style();
                if (style != null && !style.equals(dev.tamboui.style.Style.EMPTY)) {
                    sb.append(AnsiStringBuilder.styleToAnsi(style));
                    sb.append(span.content());
                    sb.append(AnsiStringBuilder.RESET);
                } else {
                    sb.append(span.content());
                }
            }
        }
        return sb.toString();
    }
}
