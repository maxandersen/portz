package dk.xam.pview;

import dev.tamboui.backend.aesh.AeshBackend;
import dev.tamboui.inline.InlineDisplay;
import dev.tamboui.layout.Constraint;
import dev.tamboui.text.Text;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.terminal.Connection;

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

    public static void renderPortsTable(List<PortEntry> entries, boolean showAll, boolean group, CommandInvocation inv) {
        if (entries.isEmpty()) {
            if (showAll) {
                inv.println(Ansi.markup("[yellow]No listening ports found.[/]"));
            } else {
                inv.println(Ansi.markup("[yellow]No dev ports found.[/] Try [dim]--all[/] to include system services."));
            }
            return;
        }

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
        int totalLines = 0;

        if (group) {
            // Group by PID, preserving order of first (lowest) port
            var grouped = new java.util.LinkedHashMap<Long, List<PortEntry>>();
            for (var e : entries) {
                grouped.computeIfAbsent(e.pid(), _ -> new ArrayList<>()).add(e);
            }
            for (var g : grouped.values()) {
                var first = g.getFirst();
                String ports = g.stream().map(e -> ":" + e.port()).collect(java.util.stream.Collectors.joining("\n"));
                rows.add(Row.from(
                        Cell.from(Text.from(ports)).style(CYAN),
                        Cell.from(nameOf(first.process())),
                        Cell.from(String.valueOf(first.pid())),
                        Cell.from(tildeHome(first.process().command())).style(DIM),
                        Cell.from(projectOf(first.process())),
                        Cell.from(frameworkOf(first.process())),
                        Cell.from(first.process().uptime()),
                        statusCell(first.process().status())
                ));
                totalLines += g.size();
            }
        } else {
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
                totalLines++;
            }
        }

        var table = Table.builder()
                .header(header)
                .rows(rows)
                .widths(
                        Constraint.max(maxCol(entries, e -> ":" + e.port(), 4)),
                        Constraint.max(maxCol(entries, e -> nameOf(e.process()), 4)),
                        Constraint.max(maxCol(entries, e -> String.valueOf(e.pid()), 3)),
                        Constraint.fill(1),      // COMMAND — takes remaining space
                        Constraint.max(maxCol(entries, e -> projectOf(e.process()), 7)),
                        Constraint.max(maxCol(entries, e -> frameworkOf(e.process()), 9)),
                        Constraint.max(maxCol(entries, e -> e.process().uptime(), 6)),
                        Constraint.max(6)        // STATUS
                )
                .columnSpacing(1)
                .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        renderInline(table, totalLines, inv);

        // Footer
        String filter = showAll ? "" : " · [dim]--all to show everything[/]";
        int portCount = entries.size();
        int processCount = rows.size();
        String summary = portCount == processCount
                ? "[cyan]%d[/] %s".formatted(portCount, portCount == 1 ? "port" : "ports")
                : "[cyan]%d[/] ports across [cyan]%d[/] processes".formatted(portCount, processCount);
        inv.println(Ansi.markup(summary + " active" + filter));
        inv.println(Ansi.markup("Run [dim]ports <number>[/] for details"));
    }

    public static void renderOrphanTable(List<PortEntry> orphans, CommandInvocation inv) {
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
                        Constraint.max(maxCol(orphans, e -> String.valueOf(e.pid()), 3)),
                        Constraint.max(maxCol(orphans, e -> nameOf(e.process()), 4)),
                        Constraint.fill(1),      // PROJECT — flex column
                        Constraint.max(maxCol(orphans, e -> e.process().uptime(), 6)),
                        Constraint.max(6)        // STATUS
                )
                .columnSpacing(1)
                .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        renderInline(table, rows.size(), inv);
    }

    /**
     * Render a Table widget via tamboui InlineDisplay.
     * InlineDisplay gets terminal width from the aesh backend automatically —
     * no manual width detection needed.
     */
    static void renderInline(Table table, int rowCount, CommandInvocation inv) {
        int tableHeight = rowCount + 3; // top border + header + rows + bottom border
        try {
            var backend = createBackend();
            try (var display = InlineDisplay.withBackend(tableHeight, backend)) {
                display.render((area, buffer) -> table.render(area, buffer, new TableState()));
            }
        } catch (Exception _) {
            renderToBuffer(table, tableHeight, detectTerminalWidth(), inv);
        }
    }

    /** Fallback when no aesh Connection is available (e.g. tests). */
    private static void renderToBuffer(Table table, int height, int width, CommandInvocation inv) {
        var area = dev.tamboui.layout.Rect.of(width, height);
        var buffer = dev.tamboui.buffer.Buffer.empty(area);
        table.render(area, buffer, new TableState());
        inv.println(buffer.toAnsiString());
    }

    /**
     * Compute column width from data content.
     * WORKAROUND: tamboui/tamboui#413
     * Constraint.fit() only works in Toolkit DSL (LayoutSolver has no handler
     * for Fit at the raw widget level). We compute widths from cell values
     * and use Constraint.max() so columns can shrink on narrow terminals.
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

    /**
     * Detect real terminal width.
     * WORKAROUND: quarkusio/quarkus#55935 — Shell.connection() returns null
     * and terminal size is hardcoded to 120x40. Create a temporary AeshBackend
     * to read the real size. The close() logs a warning about session conflicts
     * with quarkus-aesh's terminal — suppressed via log level.
     */
    private static final java.util.logging.Logger AESH_LOGGER =
            java.util.logging.Logger.getLogger("org.aesh.terminal.tty.TerminalConnection");

    /**
     * Create an AeshBackend for terminal rendering.
     * WORKAROUND: quarkusio/quarkus#55935 — Shell.connection() returns null.
     * We create our own backend; suppress close warnings since quarkus-aesh
     * also holds the terminal.
     */
    static AeshBackend createBackend() throws Exception {
        AESH_LOGGER.setLevel(java.util.logging.Level.SEVERE);
        return new AeshBackend();
    }

    static int detectTerminalWidth() {
        try {
            var backend = createBackend();
            int width = backend.size().width();
            backend.close();
            return width > 0 ? width : 120;
        } catch (Exception _) {
            return 120;
        }
    }

    private static String shortenProcessName(String name) {
        if (name.contains("/")) {
            int slash = name.lastIndexOf('/');
            return slash >= 0 ? name.substring(slash + 1) : name;
        }
        return name;
    }
}
