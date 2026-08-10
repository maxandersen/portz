package dk.xam.portz;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import java.util.*;

@CommandDefinition(name = "ps", description = "Show all running dev processes")
public class PsCommand implements Command<CommandInvocation> {

    @Option(name = "all", hasValue = false, description = "Show all processes, not just dev processes")
    boolean showAll;

    private static final Style HEADER = Ansi.NO_COLOR ? Style.EMPTY : Style.EMPTY.bold();
    private static final Style DIM = Ansi.NO_COLOR ? Style.EMPTY : Style.EMPTY.dim();

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            var entries = Collector.collectAll(true);

            // Dedup by PID, keep unique processes
            var seen = new LinkedHashSet<Long>();
            var unique = new ArrayList<PortEntry>();
            for (var e : entries) {
                if (seen.add(e.pid())) unique.add(e);
            }

            // Filter
            if (!showAll) {
                unique.removeIf(e -> !e.process().isDevProcess() || e.process().isSystemProcess());
            }

            if (unique.isEmpty()) {
                inv.println(Ansi.markup("[yellow]No processes found.[/]"));
                return CommandResult.SUCCESS;
            }

            // Measure CPU (two samples 200ms apart)
            var cpuMap = measureCpu(unique);

            // Separate docker vs non-docker
            int dockerCount = 0;
            var nonDocker = new ArrayList<PortEntry>();
            for (var e : unique) {
                if (e.process().name().contains("docker")) dockerCount++;
                else nonDocker.add(e);
            }

            // Build a pid→name lookup for parent resolution
            var pidNames = new java.util.HashMap<Long, String>();
            for (var e : unique) pidNames.put(e.pid(), Renderer.nameOf(e.process()));

            var header = Row.from(
                    Cell.from("PID").style(HEADER),
                    Cell.from("NAME").style(HEADER),
                    Cell.from("PPID").style(HEADER),
                    Cell.from("CPU%").style(HEADER),
                    Cell.from("MEM").style(HEADER),
                    Cell.from("PROJECT").style(HEADER),
                    Cell.from("STACK").style(HEADER),
                    Cell.from("UPTIME").style(HEADER),
                    Cell.from("COMMAND").style(HEADER)
            );

            var rows = new ArrayList<Row>();
            for (var e : nonDocker) {
                double cpu = cpuMap.getOrDefault(e.pid(), 0.0);
                String parentInfo = resolveParent(e.process().ppid(), pidNames);
                rows.add(Row.from(
                        Cell.from(String.valueOf(e.pid())),
                        Cell.from(Renderer.nameOf(e.process())),
                        Cell.from(parentInfo).style(DIM),
                        Cell.from("%.1f".formatted(cpu)),
                        Cell.from("%dM".formatted(e.process().memoryKb() / 1024)),
                        Cell.from(Renderer.projectOf(e.process())),
                        Cell.from(Renderer.frameworkOf(e.process())),
                        Cell.from(e.process().uptime()),
                        Cell.from(summarizeCommand(e.process().command())).style(DIM)
                ));
            }

            // Docker summary row
            if (dockerCount > 0) {
                rows.add(Row.from(
                        Cell.from("-"),
                        Cell.from("docker"),
                        Cell.from("-"),
                        Cell.from("-"),
                        Cell.from("-"),
                        Cell.from("-"),
                        Cell.from("🐳 Docker · %d processes".formatted(dockerCount)),
                        Cell.from("-"),
                        Cell.from("Container runtime").style(DIM)
                ));
            }


            var table = Table.builder()
                    .header(header)
                    .rows(rows)
                    .widths(
                            Constraint.max(Renderer.maxCol(nonDocker, e -> String.valueOf(e.pid()), 3)),
                            Constraint.max(Renderer.maxCol(nonDocker, e -> Renderer.nameOf(e.process()), 4)),
                            Constraint.max(maxPpidCol(nonDocker, pidNames)),
                            Constraint.max(5),       // CPU%
                            Constraint.max(6),       // MEM
                            Constraint.max(Renderer.maxCol(nonDocker, e -> Renderer.projectOf(e.process()), 7)),
                            Constraint.max(Renderer.maxCol(nonDocker, e -> Renderer.frameworkOf(e.process()), 9)),
                            Constraint.max(Renderer.maxCol(nonDocker, e -> e.process().uptime(), 6)),
                            Constraint.fill(1)       // COMMAND
                    )
                    .columnSpacing(1)
                    .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                    .build();

            Renderer.renderInline(table, rows.size(), inv);

            inv.println(Ansi.markup("[cyan]%d[/] %s running".formatted(
                    unique.size(), unique.size() == 1 ? "process" : "processes")));
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }

    private Map<Long, Double> measureCpu(List<PortEntry> entries) {
        try {
            String pidList = String.join(",", entries.stream().map(e -> String.valueOf(e.pid())).toList());
            var sample1 = Platform.getCpuSample(pidList);
            Thread.sleep(200);
            var sample2 = Platform.getCpuSample(pidList);

            var result = new HashMap<Long, Double>();
            for (var entry : sample1.entrySet()) {
                Double cpu2 = sample2.get(entry.getKey());
                if (cpu2 != null) {
                    result.put(entry.getKey(), Math.max(0, (cpu2 - entry.getValue()) / 0.2));
                }
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static int maxPpidCol(java.util.List<PortEntry> entries, java.util.Map<Long, String> pidNames) {
        int max = 4; // "PPID" header
        for (var e : entries) max = Math.max(max, dev.tamboui.text.CharWidth.of(resolveParent(e.process().ppid(), pidNames)));
        return max;
    }

    /** Resolve parent PID to "pid:name" or just "pid" if name unknown. */
    private static String resolveParent(long ppid, java.util.Map<Long, String> pidNames) {
        if (ppid <= 1) return String.valueOf(ppid);
        String name = pidNames.get(ppid);
        if (name == null) {
            // Try ProcessHandle for processes outside our collected set
            name = ProcessHandle.of(ppid)
                    .flatMap(ph -> ph.info().command())
                    .map(c -> {
                        int slash = c.lastIndexOf('/');
                        int bslash = c.lastIndexOf('\\');
                        int sep = Math.max(slash, bslash);
                        return sep >= 0 ? c.substring(sep + 1) : c;
                    })
                    .orElse(null);
        }
        return name != null ? ppid + ":" + name : String.valueOf(ppid);
    }

    /** Strip binary path, show meaningful args (skip flags). */
    static String summarizeCommand(String cmd) {
        String[] parts = cmd.split("\\s+");
        if (parts.length == 0) return "-";

        // Get binary basename
        String binary = parts[0];
        int slash = binary.lastIndexOf('/');
        if (slash >= 0) binary = binary.substring(slash + 1);

        // Get non-flag args (up to 3)
        var args = new ArrayList<String>();
        for (int i = 1; i < parts.length && args.size() < 3; i++) {
            if (!parts[i].startsWith("-")) args.add(parts[i]);
        }

        return args.isEmpty() ? binary : binary + " " + String.join(" ", args);
    }
}
