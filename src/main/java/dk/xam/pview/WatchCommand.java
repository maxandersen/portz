package dk.xam.pview;

import dev.tamboui.inline.InlineDisplay;
import dev.tamboui.text.MarkupParser;
import dev.tamboui.widgets.table.TableState;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

@CommandDefinition(name = "watch", description = "Real-time monitoring (poll every 1s)")
public class WatchCommand implements Command<CommandInvocation> {

    @Option(name = "all", hasValue = false, description = "Show all ports including system services")
    boolean showAll;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public CommandResult execute(CommandInvocation inv) {
        inv.println(Ansi.markup("[cyan]Starting port monitor (Ctrl+C to exit)...[/]"));

        Set<Integer> previousPorts = new HashSet<>();

        try {
            var backend = Renderer.createBackend();
            // Start with a reasonable height, InlineDisplay will resize dynamically
            try (var display = InlineDisplay.withBackend(2, backend)) {
                while (true) {
                    var entries = Collector.collectAll(showAll);
                    var currentPorts = new HashSet<Integer>();
                    entries.forEach(e -> currentPorts.add(e.port()));

                    String ts = LocalTime.now().format(TIME_FMT);

                    // Log changes above the display
                    if (!previousPorts.isEmpty()) {
                        for (int p : currentPorts) {
                            if (!previousPorts.contains(p)) {
                                var entry = entries.stream().filter(e -> e.port() == p).findFirst().orElse(null);
                                if (entry != null) {
                                    String fw = entry.process().framework() != null ? entry.process().framework().displayName() : "Unknown";
                                    String proj = entry.process().projectName() != null ? entry.process().projectName() : entry.process().name();
                                    display.println(MarkupParser.parse(
                                            "[dim]%s[/] [green]●[/] [cyan]:%d[/] started — %s / %s / %s".formatted(
                                                    ts, p, entry.process().name(), fw, proj)));
                                }
                            }
                        }
                        for (int p : previousPorts) {
                            if (!currentPorts.contains(p)) {
                                display.println(MarkupParser.parse(
                                        "[dim]%s[/] [red]✕[/] [cyan]:%d[/] stopped".formatted(ts, p)));
                            }
                        }
                    }

                    // Build the table
                    var table = Renderer.buildPortsTable(entries, showAll, true);
                    int tableHeight = table.height();

                    // Render in-place — InlineDisplay redraws without scrolling
                    display.render((area, buffer) ->
                            table.table().render(area, buffer, new TableState()), tableHeight, -1, -1);

                    previousPorts = currentPorts;
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}
