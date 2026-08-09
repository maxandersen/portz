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
                Ansi.println(inv, Ansi.green("✓ No orphaned processes found."));
                return CommandResult.SUCCESS;
            }

            inv.println(String.format("%s %s orphaned process%s:",
                    Ansi.yellow("Found"), Ansi.yellow(String.valueOf(orphans.size())),
                    orphans.size() == 1 ? "" : "es"));
            inv.println("");

            Renderer.renderOrphanTable(orphans, inv);
            inv.println("");

            for (var entry : orphans) {
                String prompt = String.format("%s PID %s %s [y/N/a(ll)/q(uit)]: ",
                        Ansi.yellow("Kill"), Ansi.bold(String.valueOf(entry.pid())),
                        Ansi.dim(entry.process().name()));
                String raw = inv.getShell().readLine(new Prompt(prompt));
                String choice = raw != null ? raw.trim().toLowerCase() : "";

                switch (choice) {
                    case "y" -> Platform.killGraceful(entry.pid());
                    case "a" -> {
                        Ansi.println(inv, Ansi.yellow("Killing all orphans..."));
                        orphans.forEach(e -> Platform.killGraceful(e.pid()));
                        Ansi.println(inv, "\n" + Ansi.green("✓ Cleanup complete."));
                        return CommandResult.SUCCESS;
                    }
                    case "q" -> {
                        Ansi.println(inv, Ansi.dim("Cancelled."));
                        return CommandResult.SUCCESS;
                    }
                    default -> Ansi.println(inv, Ansi.dim("Skipped."));
                }
            }
            Ansi.println(inv, "\n" + Ansi.green("✓ Cleanup complete."));
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            Ansi.println(inv, Ansi.red("Error: " + e.getMessage()));
            return CommandResult.FAILURE;
        }
    }
}
