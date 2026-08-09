package dk.xam.pview;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.readline.prompt.Prompt;

@CommandDefinition(name = "clean", description = "Find and interactively kill orphaned processes")
public class CleanCommand implements Command<CommandInvocation> {

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            var entries = Collector.collectAll(true);
            var orphans = entries.stream()
                    .filter(e -> (e.process().status() == ProcessStatus.ORPHANED || e.process().status() == ProcessStatus.ZOMBIE)
                            && e.process().isDevProcess())
                    .toList();

            if (orphans.isEmpty()) {
                inv.println(Ansi.markup("[green]✓ No orphaned processes found.[/]"));
                return CommandResult.SUCCESS;
            }

            inv.println(Ansi.markup("[yellow]Found %d[/] orphaned process%s".formatted(
                    orphans.size(), orphans.size() == 1 ? ":" : "es:")));
            inv.println("");

            Renderer.renderOrphanTable(orphans, inv);
            inv.println("");

            for (var entry : orphans) {
                String prompt = Ansi.markup("[yellow]Kill[/] PID [bold]%d[/] [dim]%s[/] [[y/N/a(ll)/q(uit)]]: ".formatted(
                        entry.pid(), entry.process().name()));
                String raw = inv.getShell().readLine(new Prompt(prompt));
                String choice = raw != null ? raw.trim().toLowerCase() : "";

                switch (choice) {
                    case "y" -> Platform.killGraceful(entry.pid());
                    case "a" -> {
                        inv.println(Ansi.markup("[yellow]Killing all orphans...[/]"));
                        orphans.forEach(e -> Platform.killGraceful(e.pid()));
                        inv.println(Ansi.markup("\n[green]✓ Cleanup complete.[/]"));
                        return CommandResult.SUCCESS;
                    }
                    case "q" -> {
                        inv.println(Ansi.markup("[dim]Cancelled.[/]"));
                        return CommandResult.SUCCESS;
                    }
                    default -> inv.println(Ansi.markup("[dim]Skipped.[/]"));
                }
            }
            inv.println(Ansi.markup("\n[green]✓ Cleanup complete.[/]"));
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}
