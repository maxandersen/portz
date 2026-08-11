package dk.xam.portz;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import java.nio.file.Files;
import java.nio.file.Path;

@CommandDefinition(name = "portz",
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

    @Option(name = "parent", hasValue = false, description = "Show parent PID column")
    boolean parent;

    @Option(name = "compact", hasValue = false, defaultValue = "true", negatable = true,
            description = "Compact binary paths (default: true, use --no-compact for full paths)")
    boolean compact;

    @Option(name = "save", description = "Save output as SVG to the given file path")
    String save;

    @Option(name = "width", description = "Terminal width for SVG export (default: 120)", defaultValue = "120")
    int width;

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            if (port != null) {
                DetailView.show(port, inv);
            } else {
                var entries = Collector.collectAll(showAll);
                if (save != null) {
                    var built = Renderer.buildPortsTable(entries, showAll, group, parent, compact);
                    String svg = Renderer.renderToSvg(built.table(), built.height(), width);
                    Files.writeString(Path.of(save), svg);
                    inv.println(Ansi.markup("Saved SVG to [cyan]%s[/]".formatted(save)));
                } else {
                    Renderer.renderPortsTable(entries, showAll, group, parent, compact, inv);
                }
            }
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}
