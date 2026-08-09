package dk.xam.pview;

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
        inv.println("");

        Set<Integer> previousPorts = new HashSet<>();
        boolean first = true;

        try {
            while (true) {
                var entries = Collector.collectAll(showAll);
                var currentPorts = new HashSet<Integer>();
                entries.forEach(e -> currentPorts.add(e.port()));

                String ts = LocalTime.now().format(TIME_FMT);

                if (!first) {
                    for (int p : currentPorts) {
                        if (!previousPorts.contains(p)) {
                            var entry = entries.stream().filter(e -> e.port() == p).findFirst().orElse(null);
                            if (entry != null) {
                                String fw = entry.process().framework() != null ? entry.process().framework().displayName() : "Unknown";
                                String proj = entry.process().projectName() != null ? entry.process().projectName() : entry.process().name();
                                inv.println(Ansi.markup("[dim]%s[/] %s [cyan]:%d[/] started — %s / %s / %s".formatted(
                                        ts, entry.process().status().symbol(), p, entry.process().name(), fw, proj)));
                            }
                        }
                    }
                    for (int p : previousPorts) {
                        if (!currentPorts.contains(p)) {
                            inv.println(Ansi.markup("[dim]%s[/] [red]✕[/] [cyan]:%d[/] stopped".formatted(ts, p)));
                        }
                    }
                    inv.print("\033[2J\033[H");
                }

                Renderer.renderPortsTable(entries, showAll, inv);
                previousPorts = currentPorts;
                first = false;
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}
