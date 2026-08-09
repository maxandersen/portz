package dk.xam.pview;

import dev.tamboui.inline.InlineDisplay;
import dev.tamboui.text.MarkupParser;
import dev.tamboui.widgets.table.TableState;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CommandDefinition(name = "watch", description = "Real-time monitoring (poll every 1s)")
public class WatchCommand implements Command<CommandInvocation> {

    @Option(name = "all", hasValue = false, description = "Show all ports including system services")
    boolean showAll;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long GHOST_TTL_SECONDS = 5;

    record Ghost(PortEntry entry, Instant diedAt) {}

    @Override
    public CommandResult execute(CommandInvocation inv) {
        inv.println(Ansi.markup("[cyan]Starting port monitor (q or Ctrl+C to exit)...[/]"));

        var previousByPid = new HashMap<Long, PortEntry>();
        var ghosts = new LinkedHashMap<Long, Ghost>(); // pid -> ghost

        try {
            var backend = Renderer.createBackend();
            try (var display = InlineDisplay.withBackend(2, backend)) {
                while (true) {
                    var entries = Collector.collectAll(showAll);
                    var currentByPid = new HashMap<Long, PortEntry>();
                    entries.forEach(e -> currentByPid.putIfAbsent(e.pid(), e));

                    String ts = LocalTime.now().format(TIME_FMT);
                    Instant now = Instant.now();

                    // Detect changes
                    if (!previousByPid.isEmpty()) {
                        // New ports
                        for (var e : entries) {
                            if (!previousByPid.containsKey(e.pid())) {
                                String fw = e.process().framework() != null ? e.process().framework().displayName() : "Unknown";
                                String proj = e.process().projectName() != null ? e.process().projectName() : e.process().name();
                                display.println(MarkupParser.parse(
                                        "[dim]%s[/] [green]●[/] [cyan]:%d[/] started — %s / %s / %s".formatted(
                                                ts, e.port(), e.process().name(), fw, proj)));
                                // Remove from ghosts if it came back
                                ghosts.remove(e.pid());
                            }
                        }
                        // Dead processes → become ghosts
                        for (var prev : previousByPid.entrySet()) {
                            if (!currentByPid.containsKey(prev.getKey())) {
                                ghosts.put(prev.getKey(), new Ghost(prev.getValue(), now));
                                display.println(MarkupParser.parse(
                                        "[dim]%s[/] [red]✕[/] [cyan]:%d[/] stopped".formatted(
                                                ts, prev.getValue().port())));
                            }
                        }
                    }

                    // Prune expired ghosts
                    ghosts.entrySet().removeIf(e ->
                            now.getEpochSecond() - e.getValue().diedAt().getEpochSecond() > GHOST_TTL_SECONDS);

                    // Build table with ghost rows and death times
                    var ghostEntries = ghosts.values().stream().map(Ghost::entry).toList();
                    var deathTimes = new HashMap<Long, Instant>();
                    ghosts.forEach((pid, g) -> deathTimes.put(pid, g.diedAt()));
                    var built = Renderer.buildPortsTable(entries, showAll, true, ghostEntries, deathTimes);

                    // Render in-place
                    display.render((area, buffer) ->
                            built.table().render(area, buffer, new TableState()), built.height(), -1, -1);

                    previousByPid = currentByPid;

                    // Wait ~1s, but poll for 'q' keypress to quit
                    int ch = backend.read(1000);
                    if (ch == 'q' || ch == 'Q') return CommandResult.SUCCESS;
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
