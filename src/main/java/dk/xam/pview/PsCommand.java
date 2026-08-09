package dk.xam.pview;

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

    private static final Style HEADER = Style.EMPTY.bold();
    private static final Style DIM = Style.EMPTY.dim();

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

            var header = Row.from(
                    Cell.from("PID").style(HEADER),
                    Cell.from("PROCESS").style(HEADER),
                    Cell.from("CPU%").style(HEADER),
                    Cell.from("MEM").style(HEADER),
                    Cell.from("PROJECT").style(HEADER),
                    Cell.from("FRAMEWORK").style(HEADER),
                    Cell.from("UPTIME").style(HEADER),
                    Cell.from("WHAT").style(HEADER)
            );

            var rows = new ArrayList<Row>();
            for (var e : nonDocker) {
                double cpu = cpuMap.getOrDefault(e.pid(), 0.0);
                String fw = e.process().framework() != null
                        ? e.process().framework().emoji() + " " + e.process().framework().displayName()
                        : "-";
                rows.add(Row.from(
                        Cell.from(String.valueOf(e.pid())),
                        Cell.from(e.process().name()),
                        Cell.from("%.1f".formatted(cpu)),
                        Cell.from("%dM".formatted(e.process().memoryKb() / 1024)),
                        Cell.from(e.process().projectName() != null ? e.process().projectName() : "-"),
                        Cell.from(fw),
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
                        Cell.from("🐳 Docker · %d processes".formatted(dockerCount)),
                        Cell.from("-"),
                        Cell.from("Container runtime").style(DIM)
                ));
            }

            int width = Renderer.getTerminalWidth(inv);
            var table = Table.builder()
                    .header(header)
                    .rows(rows)
                    .widths(
                            Constraint.length(7),    // PID
                            Constraint.length(10),   // PROCESS
                            Constraint.length(5),    // CPU%
                            Constraint.length(6),    // MEM
                            Constraint.length(18),   // PROJECT
                            Constraint.length(14),   // FRAMEWORK
                            Constraint.length(8),    // UPTIME
                            Constraint.fill(1)       // WHAT
                    )
                    .columnSpacing(1)
                    .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                    .build();

            int tableHeight = rows.size() + 3;
            var area = Rect.of(width, tableHeight);
            var buffer = Buffer.empty(area);
            table.render(area, buffer, new TableState());
            inv.println(buffer.toAnsiString());

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
