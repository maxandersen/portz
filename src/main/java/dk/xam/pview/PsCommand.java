package dk.xam.pview;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "ps", description = "Show all running dev processes")
public class PsCommand implements Runnable {

    @Option(names = "--all", description = "Show all processes, not just dev processes")
    boolean showAll;

    @Override
    public void run() {
        try {
            var entries = Collector.collectAll(showAll);
            if (entries.isEmpty()) {
                System.out.println(Ansi.yellow("No dev processes found."));
                return;
            }
            // Reuse the ports table for now — same data, same view
            Renderer.renderPortsTable(entries, showAll);
        } catch (Exception e) {
            System.err.println(Ansi.red("Error: " + e.getMessage()));
        }
    }
}
