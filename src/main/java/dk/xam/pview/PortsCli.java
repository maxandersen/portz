package dk.xam.pview;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@TopCommand
@Command(name = "ports",
         description = "A beautiful CLI tool to inspect and manage processes listening on your machine's ports",
         mixinStandardHelpOptions = true,
         subcommands = { PsCommand.class, WatchCommand.class, CleanCommand.class })
public class PortsCli implements Runnable {

    @Option(names = "--all", description = "Show all ports including system services")
    boolean showAll;

    @Parameters(index = "0", arity = "0..1", description = "Port number to inspect in detail")
    Integer port;

    @Override
    public void run() {
        try {
            if (port != null) {
                DetailView.show(port);
            } else {
                var entries = Collector.collectAll(showAll);
                Renderer.renderPortsTable(entries, showAll);
            }
        } catch (Exception e) {
            System.err.println(Ansi.red("Error: " + e.getMessage()));
            System.exit(1);
        }
    }
}
