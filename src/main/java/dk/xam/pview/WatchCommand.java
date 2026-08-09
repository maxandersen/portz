package dk.xam.pview;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

@Command(name = "watch", description = "Real-time monitoring (poll every 1s)")
public class WatchCommand implements Runnable {

    @Option(names = "--all", description = "Show all ports including system services")
    boolean showAll;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void run() {
        System.out.println(Ansi.cyan("Starting port monitor (Ctrl+C to exit)..."));
        System.out.println();

        Set<Integer> previousPorts = new HashSet<>();
        boolean first = true;

        try {
            while (true) {
                var entries = Collector.collectAll(showAll);
                var currentPorts = new HashSet<Integer>();
                entries.forEach(e -> currentPorts.add(e.port()));

                String ts = LocalTime.now().format(TIME_FMT);

                if (!first) {
                    // Log changes
                    for (int p : currentPorts) {
                        if (!previousPorts.contains(p)) {
                            var entry = entries.stream().filter(e -> e.port() == p).findFirst().orElse(null);
                            if (entry != null) {
                                String fw = entry.process().framework() != null ? entry.process().framework().displayName() : "Unknown";
                                String proj = entry.process().projectName() != null ? entry.process().projectName() : entry.process().name();
                                System.out.printf("[%s] %s %s started — %s / %s / %s%n",
                                        Ansi.dim(ts), entry.process().status().symbol(),
                                        Ansi.cyan(":" + p), entry.process().name(), fw, proj);
                            }
                        }
                    }
                    for (int p : previousPorts) {
                        if (!currentPorts.contains(p)) {
                            System.out.printf("[%s] %s %s stopped%n", Ansi.dim(ts), Ansi.red("✕"), Ansi.cyan(":" + p));
                        }
                    }
                    // Clear screen
                    System.out.print("\033[2J\033[H");
                }

                Renderer.renderPortsTable(entries, showAll);
                previousPorts = currentPorts;
                first = false;
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println(Ansi.red("Error: " + e.getMessage()));
        }
    }
}
