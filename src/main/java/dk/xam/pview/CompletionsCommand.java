package dk.xam.pview;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;
import org.aesh.util.completer.ShellCompletionGenerator;
import org.aesh.util.completer.ShellCompletionGenerator.ShellType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@CommandDefinition(name = "completions", description = "Generate shell completion scripts")
public class CompletionsCommand implements Command<CommandInvocation> {

    @Option(name = "shell", shortName = 's', description = "Target shell: BASH, ZSH, FISH, or POWERSHELL",
            defaultValue = "BASH")
    ShellType shell;

    @Option(name = "output", shortName = 'o', description = "Output directory (default: ./completions)")
    String output;

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            var builder = new AeshCommandContainerBuilder<>();
            var container = builder.create(PortsCli.class);
            String programName = "ports";

            var generator = ShellCompletionGenerator.forShell(shell);
            String script = generator.generate(container.getParser(), programName);

            Path dir = Path.of(output != null ? output : "completions");
            Files.createDirectories(dir);
            Path file = dir.resolve(programName + shell.fileExtension());
            Files.writeString(file, script, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            inv.println(Ansi.markup("[green]✓[/] Completion script written to: [bold]%s[/]".formatted(file)));
            inv.println(Ansi.markup("[dim]Install with:[/]"));
            switch (shell) {
                case BASH -> inv.println(Ansi.markup("  [dim]source %s[/]".formatted(file)));
                case ZSH -> inv.println(Ansi.markup("  [dim]source %s[/]".formatted(file)));
                case FISH -> inv.println(Ansi.markup("  [dim]cp %s ~/.config/fish/completions/[/]".formatted(file)));
                case PWSH -> inv.println(Ansi.markup("  [dim]. %s[/]".formatted(file)));
            }
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}
