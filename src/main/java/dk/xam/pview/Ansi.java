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
