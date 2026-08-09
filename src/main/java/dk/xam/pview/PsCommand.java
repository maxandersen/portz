package dk.xam.pview;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

@CommandDefinition(name = "ps", description = "Show all running dev processes")
public class PsCommand implements Command<CommandInvocation> {

    @Option(name = "all", hasValue = false, description = "Show all processes, not just dev processes")
    boolean showAll;

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            var entries = Collector.collectAll(showAll);
            if (entries.isEmpty()) {
                Ansi.println(inv, Ansi.yellow("No dev processes found."));
                return CommandResult.SUCCESS;
            }
            Renderer.renderPortsTable(entries, showAll, inv);
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            Ansi.println(inv, Ansi.red("Error: " + e.getMessage()));
            return CommandResult.FAILURE;
        }
    }
}
