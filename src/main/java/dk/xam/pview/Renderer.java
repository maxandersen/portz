package dk.xam.pview;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.terminal.tty.Size;

import java.util.ArrayList;
import java.util.List;

public class Renderer {

    private static final String HOME = System.getProperty("user.home");
    private static final Style CYAN = Style.EMPTY.fg(Color.CYAN);
    private static final Style GREEN = Style.EMPTY.fg(Color.GREEN);
    private static final Style YELLOW = Style.EMPTY.fg(Color.YELLOW);
    private static final Style RED = Style.EMPTY.fg(Color.RED);
    private static final Style DIM = Style.EMPTY.dim();
    private static final Style HEADER = Style.EMPTY.bold();

    public static void renderPortsTable(List<PortEntry> entries, boolean showAll, CommandInvocation inv) {
        if (entries.isEmpty()) {
            inv.println(Ansi.markup("[yellow]No listening ports found.[/]"));
            return;
        }

        int width = getTerminalWidth(inv);

        var header = Row.from(
                Cell.from("PORT").style(HEADER),
                Cell.from("NAME").style(HEADER),
                Cell.from("PID").style(HEADER),
                Cell.from("COMMAND").style(HEADER),
                Cell.from("PROJECT").style(HEADER),
                Cell.from("FRAMEWORK").style(HEADER),
                Cell.from("UPTIME").style(HEADER),
                Cell.from("STATUS").style(HEADER)
        );

        var rows = new ArrayList<Row>();
        for (var e : entries) {
            rows.add(Row.from(
                    Cell.from(":" + e.port()).style(CYAN),
                    Cell.from(nameOf(e.process())),
                    Cell.from(String.valueOf(e.pid())),
                    Cell.from(tildeHome(e.process().command())).style(DIM),
                    Cell.from(projectOf(e.process())),
                    Cell.from(frameworkOf(e.process())),
                    Cell.from(e.process().uptime()),
                    statusCell(e.process().status())
            ));
        }

        var table = Table.builder()
                .header(header)
                .rows(rows)
                .widths(
                        Constraint.length(maxCol(entries, e -> ":" + e.port(), 4)),
                        Constraint.length(maxCol(entries, e -> nameOf(e.process()), 4)),
                        Constraint.length(maxCol(entries, e -> String.valueOf(e.pid()), 3)),
                        Constraint.fill(1),      // COMMAND — takes remaining space
                        Constraint.length(maxCol(entries, e -> projectOf(e.process()), 7)),
                        Constraint.length(maxCol(entries, e -> frameworkOf(e.process()), 9)),
                        Constraint.length(maxCol(entries, e -> e.process().uptime(), 6)),
                        Constraint.length(6)     // STATUS
                )
                .columnSpacing(1)
                .block(Block.builder().borders(dev.tamboui.widgets.block.Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        int tableHeight = rows.size() + 3; // top border + header + rows + bottom border
        var area = Rect.of(width, tableHeight);
        var buffer = Buffer.empty(area);
        table.render(area, buffer, new TableState());
        inv.println(buffer.toAnsiString());

        // Footer
        String filter = showAll ? "" : " · [dim]--all to show everything[/]";
        inv.println(Ansi.markup("[cyan]%d[/] %s active%s".formatted(
                entries.size(), entries.size() == 1 ? "port" : "ports", filter)));
        inv.println(Ansi.markup("Run [dim]ports <number>[/] for details"));
    }

    public static void renderOrphanTable(List<PortEntry> orphans, CommandInvocation inv) {
        int width = getTerminalWidth(inv);

        var header = Row.from(
                Cell.from("PID").style(HEADER),
                Cell.from("NAME").style(HEADER),
                Cell.from("PROJECT").style(HEADER),
                Cell.from("UPTIME").style(HEADER),
                Cell.from("STATUS").style(HEADER)
        );

        var rows = new ArrayList<Row>();
        for (var e : orphans) {
            rows.add(Row.from(
                    Cell.from(String.valueOf(e.pid())),
                    Cell.from(nameOf(e.process())),
                    Cell.from(projectOf(e.process())),
                    Cell.from(e.process().uptime()),
                    statusCell(e.process().status())
            ));
        }

        var table = Table.builder()
                .header(header)
                .rows(rows)
                .widths(
                        Constraint.length(maxCol(orphans, e -> String.valueOf(e.pid()), 3)),
                        Constraint.length(maxCol(orphans, e -> nameOf(e.process()), 4)),
                        Constraint.fill(1),      // PROJECT
                        Constraint.length(maxCol(orphans, e -> e.process().uptime(), 6)),
                        Constraint.length(6)     // STATUS
                )
                .columnSpacing(1)
                .block(Block.builder().borders(dev.tamboui.widgets.block.Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        int tableHeight = rows.size() + 3;
        var area = Rect.of(width, tableHeight);
        var buffer = Buffer.empty(area);
        table.render(area, buffer, new TableState());
        inv.println(buffer.toAnsiString());
    }

    /**
     * Compute column width from data content.
     * ponytail: Constraint.fit() only works in Toolkit DSL (needs preferredWidth()),
     * not with raw Table widget. So we compute widths from actual cell values.
     */
    static int maxCol(List<PortEntry> entries, java.util.function.Function<PortEntry, String> fn, int headerLen) {
        int max = headerLen;
        for (var e : entries) max = Math.max(max, fn.apply(e).length());
        return max;
    }

    // === Shared cell helpers for consistent formatting across tables ===

    static String nameOf(ProcessInfo p) {
        return shortenProcessName(p.name());
    }

    static String projectOf(ProcessInfo p) {
        return p.projectName() != null ? p.projectName() : "-";
    }

    static String frameworkOf(ProcessInfo p) {
        return p.framework() != null
                ? p.framework().emoji() + " " + p.framework().displayName()
                : "-";
    }

    static Cell statusCell(ProcessStatus st) {
        Style style = switch (st) {
            case HEALTHY -> GREEN;
            case ORPHANED -> YELLOW;
            case ZOMBIE -> RED;
        };
        return Cell.from(st.rawSymbol()).style(style);
    }

    private static String tildeHome(String s) {
        return HOME != null && s.startsWith(HOME) ? "~" + s.substring(HOME.length()) : s;
    }

    /** Turn /Applications/Google Chrome.app/Contents/MacOS/Google Chrome -> Google Chrome */
    private static String shortenProcessName(String name) {
        // ponytail: name is already extracted by Collector.extractDisplayName,
        // but guard against raw paths leaking through
        if (name.contains("/")) {
            int slash = name.lastIndexOf('/');
            return slash >= 0 ? name.substring(slash + 1) : name;
        }
        return name;
    }

    static int getTerminalWidth(CommandInvocation inv) {
        int detected = 0;
        // Try aesh shell first
        try {
            Size size = inv.getShell().size();
            if (size != null && size.getWidth() > 0) detected = size.getWidth();
        } catch (Exception ignored) {}
        // Try stty via /dev/tty
        if (detected == 0) {
            try {
                var proc = new ProcessBuilder("stty", "size")
                        .redirectInput(new java.io.File("/dev/tty")).start();
                String out = new String(proc.getInputStream().readAllBytes()).trim();
                proc.waitFor();
                String[] parts = out.split("\\s+");
                if (parts.length >= 2) detected = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
        }
        // Try COLUMNS env var
        if (detected == 0) {
            try {
                String cols = System.getenv("COLUMNS");
                if (cols != null) detected = Integer.parseInt(cols);
            } catch (Exception ignored) {}
        }
        // ponytail: 80 is the universal "I don't know" default.
        // Real wide terminals report actual width. Treat <=80 as unknown
        // and use a generous default so the table isn't squished.
        return detected > 80 ? detected : 140;
    }
}
