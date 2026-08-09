package dk.xam.pview;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
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
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.readline.prompt.Prompt;

import java.util.ArrayList;

public class DetailView {

    private static final Style LABEL = Style.EMPTY.bold();
    private static final Style DIM = Style.EMPTY.dim();
    private static final Style CYAN = Style.EMPTY.fg(Color.CYAN);
    private static final Style GREEN = Style.EMPTY.fg(Color.GREEN);

    public static void show(int port, CommandInvocation inv) throws Exception {
        var entries = Collector.collectAll(true);
        var entry = entries.stream().filter(e -> e.port() == port).findFirst()
                .orElseThrow(() -> new RuntimeException("No process found listening on port " + port));

        var proc = entry.process();

        // === Info table ===
        var infoRows = new ArrayList<Row>();
        infoRows.add(Row.from(Cell.from("Port:").style(LABEL), Cell.from(":" + port).style(CYAN)));
        infoRows.add(Row.from(Cell.from("Process:").style(LABEL), Cell.from(proc.name())));
        infoRows.add(Row.from(Cell.from("PID:").style(LABEL), Cell.from(String.valueOf(proc.pid()))));
        if (proc.projectName() != null)
            infoRows.add(Row.from(Cell.from("Project:").style(LABEL), Cell.from(proc.projectName())));
        if (proc.cwd() != null)
            infoRows.add(Row.from(Cell.from("Path:").style(LABEL), Cell.from(proc.cwd()).style(DIM)));
        if (proc.framework() != null)
            infoRows.add(Row.from(Cell.from("Framework:").style(LABEL),
                    Cell.from(proc.framework().emoji() + " " + proc.framework().displayName())));
        if (proc.gitBranch() != null)
            infoRows.add(Row.from(Cell.from("Git Branch:").style(LABEL),
                    Cell.from("🌿 " + proc.gitBranch()).style(GREEN)));
        infoRows.add(Row.from(Cell.from("Uptime:").style(LABEL), Cell.from(proc.uptime())));
        infoRows.add(Row.from(Cell.from("Memory:").style(LABEL), Cell.from("%.1f MB".formatted(proc.memoryMb()))));
        infoRows.add(Row.from(Cell.from("Parent PID:").style(LABEL), Cell.from(String.valueOf(proc.ppid()))));
        infoRows.add(Row.from(Cell.from("Status:").style(LABEL), Renderer.statusCell(proc.status())));

        var infoTable = Table.builder()
                .rows(infoRows)
                .widths(Constraint.max(12), Constraint.fill(1))
                .columnSpacing(1)
                .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        // === Command table ===
        var cmdRows = new ArrayList<Row>();
        for (String arg : proc.command().split("\\s+")) {
            cmdRows.add(Row.from(Cell.from(arg).style(DIM)));
        }

        var cmdTable = Table.builder()
                .header(Row.from(Cell.from("Command:").style(LABEL)))
                .rows(cmdRows)
                .widths(Constraint.fill(1))
                .block(Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).build())
                .build();

        // Render to buffer with detected terminal width
        int infoHeight = infoRows.size() + 2;
        int cmdHeight = cmdRows.size() + 3;
        int totalHeight = infoHeight + cmdHeight;
        int width = Renderer.detectTerminalWidth();

        var area = Rect.of(width, totalHeight);
        var buffer = Buffer.empty(area);
        var areas = Layout.vertical()
                .constraints(Constraint.length(infoHeight), Constraint.length(cmdHeight))
                .split(area);
        infoTable.render(areas.get(0), buffer, new TableState());
        cmdTable.render(areas.get(1), buffer, new TableState());
        inv.println(buffer.toAnsiString());

        inv.println("");
        String prompt = Ansi.markup("[yellow]Kill this process?[/] [dim](PID %d)[/] [[y/N]]: ".formatted(proc.pid()));
        String raw = inv.getShell().readLine(new Prompt(prompt));
        String input = raw != null ? raw.trim().toLowerCase() : "";
        if ("y".equals(input)) {
            Platform.killGraceful(proc.pid());
        } else {
            inv.println(Ansi.markup("[dim]Cancelled.[/]"));
        }
    }
}
