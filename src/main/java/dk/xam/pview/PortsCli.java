package dk.xam.pview;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

@CommandDefinition(name = "ports",
        description = "A beautiful CLI tool to inspect and manage processes listening on your machine's ports",
        groupCommands = { PsCommand.class, WatchCommand.class, CleanCommand.class, CompletionsCommand.class })
public class PortsCli implements Command<CommandInvocation> {

    @Option(name = "all", hasValue = false, description = "Show all ports including system services")
    boolean showAll;

    @Option(name = "port", shortName = 'p', description = "Port number to inspect in detail")
    Integer port;

    @Option(name = "group", hasValue = false, defaultValue = "true", negatable = true,
            description = "Group ports by process (default: true, use --no-group to expand)")
    boolean group;

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            if (port != null) {
                DetailView.show(port, inv);
            } else {
                var entries = Collector.collectAll(showAll);
                Renderer.renderPortsTable(entries, showAll, group, inv);
            }
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}
