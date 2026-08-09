package dk.xam.portz;

import dev.tamboui.backend.aesh.AeshBackend;
import dev.tamboui.inline.InlineDisplay;
import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.aesh.command.invocation.CommandInvocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class Renderer {

    private static final String HOME = System.getProperty("user.home");
    private static final Style NEW_ROW = color(Style.EMPTY.fg(Color.LIGHT_GREEN));
    private static final Style DEAD_ROW = color(Style.EMPTY.fg(Color.LIGHT_RED));
    private static final Style CYAN = color(Style.EMPTY.fg(Color.CYAN));
    private static final Style GREEN = color(Style.EMPTY.fg(Color.GREEN));
    private static final Style YELLOW = color(Style.EMPTY.fg(Color.YELLOW));
    private static final Style RED = color(Style.EMPTY.fg(Color.RED));
    private static final Style DIM = color(Style.EMPTY.dim());
    private static final Style HEADER = color(Style.EMPTY.bold());

    private static Style color(Style s) { return Ansi.NO_COLOR ? Style.EMPTY : s; }

    record BuiltTable(Table table, int height) {}

    /** Build a ports table without rendering. Used by watch mode and renderPortsTable. */
    static BuiltTable buildPortsTable(List<PortEntry> entries, boolean showAll, boolean group) {
        return buildPortsTable(entries, showAll, group, List.of());
    }

    /** Build a ports table with optional ghost (recently dead) entries. */
    static BuiltTable buildPortsTable(List<PortEntry> entries, boolean showAll, boolean group,
                                       List<PortEntry> ghosts) {
        return buildPortsTable(entries, showAll, group, ghosts, Map.of());
    }

    static BuiltTable buildPortsTable(List<PortEntry> entries, boolean showAll, boolean group,
                                       List<PortEntry> ghosts, Map<Long, java.time.Instant> deathTimes) {
        // Merge live + ghost entries, sorted by port, for proper interleaving
        var ghostPids = new HashSet<Long>();
        ghosts.forEach(e -> ghostPids.add(e.pid()));
        var allEntries = new ArrayList<>(entries);
        allEntries.addAll(ghosts);
        allEntries.sort(java.util.Comparator.comparingInt(PortEntry::port));

        var header = Row.from(
                Cell.from("PORT").style(HEADER), Cell.from("NAME").style(HEADER),
                Cell.from("PID").style(HEADER), Cell.from("COMMAND").style(HEADER),
                Cell.from("PROJECT").style(HEADER), Cell.from("FRAMEWORK").style(HEADER),
                Cell.from("UPTIME").style(HEADER), Cell.from("STATUS").style(HEADER));

        var rows = new ArrayList<Row>();
        int totalLines = 0;

        if (group) {
            var grouped = new java.util.LinkedHashMap<Long, List<PortEntry>>();
            for (var e : allEntries) grouped.computeIfAbsent(e.pid(), _ -> new ArrayList<>()).add(e);
            for (var g : grouped.values()) {
                var first = g.getFirst();
                boolean dead = ghostPids.contains(first.pid());
                boolean recent = !dead && first.process().uptimeSeconds() < 60;
                String ports = g.stream().map(e -> ":" + e.port()).collect(java.util.stream.Collectors.joining("\n"));
                var row = Row.from(
                        Cell.from(Text.from(ports)).style(dead ? DEAD_ROW : CYAN), Cell.from(nameOf(first.process())),
                        Cell.from(String.valueOf(first.pid())), Cell.from(tildeHome(first.process().command())).style(dead ? DEAD_ROW : DIM),
                        Cell.from(projectOf(first.process())), Cell.from(frameworkOf(first.process())),
                        Cell.from(dead ? deadTime(first.pid(), deathTimes) : first.process().uptime()), Cell.from(dead ? "✕" : first.process().status().rawSymbol()));
                rows.add(dead ? row.style(DEAD_ROW) : recent ? row.style(NEW_ROW) : row);
                totalLines += g.size();
            }
        } else {
            for (var e : allEntries) {
                boolean dead = ghostPids.contains(e.pid());
                boolean recent = !dead && e.process().uptimeSeconds() < 60;
                var row = Row.from(
                        Cell.from(":" + e.port()).style(dead ? DEAD_ROW : CYAN), Cell.from(nameOf(e.process())),
                        Cell.from(String.valueOf(e.pid())), Cell.from(tildeHome(e.process().command())).style(dead ? DEAD_ROW : DIM),
                        Cell.from(projectOf(e.process())), Cell.from(frameworkOf(e.process())),
                        Cell.from(dead ? deadTime(e.pid(), deathTimes) : e.process().uptime()), Cell.from(dead ? "✕" : e.process().status().rawSymbol()));
                rows.add(dead ? row.style(DEAD_ROW) : recent ? row.style(NEW_ROW) : row);
                totalLines++;
            }
        }

        var table = Table.builder()
                .header(header).rows(rows)
                .widths(Constraint.max(maxCol(allEntries, e -> ":" + e.port(), 4)),
                        Constraint.max(maxCol(allEntries, e -> nameOf(e.process()), 4)),
                        Constraint.max(maxCol(allEntries, e -> String.valueOf(e.pid()), 3)),
                        Constraint.fill(1),
                        Constraint.max(maxCol(allEntries, e -> projectOf(e.process()), 7)),
                        Constraint.max(maxCol(allEntries, e -> frameworkOf(e.process()), 9)),
                        Constraint.max(maxCol(allEntries, e -> e.process().uptime(), 6)),
                        Constraint.max(6))
                .columnSpacing(1)
                .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        return new BuiltTable(table, totalLines + 3);
    }

    public static void renderPortsTable(List<PortEntry> entries, boolean showAll, boolean group, CommandInvocation inv) {
        if (entries.isEmpty()) {
            inv.println(Ansi.markup(showAll
                    ? "[yellow]No listening ports found.[/]"
                    : "[yellow]No dev ports found.[/] Try [dim]--all[/] to include system services."));
            return;
        }

        var built = buildPortsTable(entries, showAll, group);
        renderInline(built.table(), built.height(), inv);

        // Footer
        String filter = showAll ? "" : " · [dim]--all to show everything[/]";
        int portCount = entries.size();
        int processCount = (int) entries.stream().map(PortEntry::pid).distinct().count();
        String summary = portCount == processCount
                ? "[cyan]%d[/] %s".formatted(portCount, portCount == 1 ? "port" : "ports")
                : "[cyan]%d[/] ports across [cyan]%d[/] processes".formatted(portCount, processCount);
        inv.println(Ansi.markup(summary + " active" + filter));
        inv.println(Ansi.markup("Run [dim]ports <number>[/] for details"));
    }

    public static void renderOrphanTable(List<PortEntry> orphans, CommandInvocation inv) {
        var header = Row.from(
                Cell.from("PID").style(HEADER), Cell.from("NAME").style(HEADER),
                Cell.from("PROJECT").style(HEADER), Cell.from("UPTIME").style(HEADER),
                Cell.from("STATUS").style(HEADER));

        var rows = new ArrayList<Row>();
        for (var e : orphans) {
            rows.add(Row.from(
                    Cell.from(String.valueOf(e.pid())), Cell.from(nameOf(e.process())),
                    Cell.from(projectOf(e.process())), Cell.from(e.process().uptime()),
                    statusCell(e.process().status())));
        }

        var table = Table.builder().header(header).rows(rows)
                .widths(Constraint.max(maxCol(orphans, e -> String.valueOf(e.pid()), 3)),
                        Constraint.max(maxCol(orphans, e -> nameOf(e.process()), 4)),
                        Constraint.fill(1),
                        Constraint.max(maxCol(orphans, e -> e.process().uptime(), 6)),
                        Constraint.max(6))
                .columnSpacing(1)
                .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        renderInline(table, rows.size() + 3, inv);
    }

    // === Rendering ===

    static void renderInline(Table table, int height, CommandInvocation inv) {
        try {
            var backend = createBackend();
            try (var display = InlineDisplay.withBackend(height, backend)) {
                display.render((area, buffer) -> table.render(area, buffer, new TableState()));
            }
        } catch (Exception _) {
            renderToBuffer(table, height, 120, inv);
        }
    }

    private static void renderToBuffer(Table table, int height, int width, CommandInvocation inv) {
        var area = dev.tamboui.layout.Rect.of(width, height);
        var buffer = dev.tamboui.buffer.Buffer.empty(area);
        table.render(area, buffer, new TableState());
        inv.println(buffer.toAnsiString());
    }

    private static final java.util.logging.Logger AESH_LOGGER =
            java.util.logging.Logger.getLogger("org.aesh.terminal.tty.TerminalConnection");

    /**
     * Create an AeshBackend for terminal rendering.
     * WORKAROUND: quarkusio/quarkus#55935 — Shell.connection() returns null.
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

    // === Column width ===

    /**
     * WORKAROUND: tamboui/tamboui#413
     * Constraint.fit() doesn't work with raw Table widget.
     */
    static int maxCol(List<PortEntry> entries, java.util.function.Function<PortEntry, String> fn, int headerLen) {
        int max = headerLen;
        for (var e : entries) max = Math.max(max, fn.apply(e).length());
        return max;
    }

    // === Shared cell helpers ===

    static String nameOf(ProcessInfo p) { return shortenProcessName(p.name()); }
    static String projectOf(ProcessInfo p) { return p.projectName() != null ? p.projectName() : "-"; }
    static String frameworkOf(ProcessInfo p) {
        return p.framework() != null ? p.framework().emoji() + " " + p.framework().displayName() : "-";
    }

    static Cell statusCell(ProcessStatus st) {
        Style style = switch (st) { case HEALTHY -> GREEN; case ORPHANED -> YELLOW; case ZOMBIE -> RED; };
        return Cell.from(st.rawSymbol()).style(style);
    }

    private static String deadTime(long pid, Map<Long, java.time.Instant> deathTimes) {
        var died = deathTimes.get(pid);
        if (died == null) return "✕";
        long secs = java.time.Duration.between(died, java.time.Instant.now()).getSeconds();
        return "-%ds".formatted(secs);
    }

    static String tildeHome(String s) {
        return HOME != null && s.startsWith(HOME) ? "~" + s.substring(HOME.length()) : s;
    }

    private static String shortenProcessName(String name) {
        if (name.contains("/")) {
            int slash = name.lastIndexOf('/');
            return slash >= 0 ? name.substring(slash + 1) : name;
        }
        return name;
    }
}
