package dk.xam.portz;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.util.completer.ShellCompletionGenerator;
import org.aesh.util.completer.ShellCompletionGenerator.ShellType;

@CommandDefinition(name = "completion", description = "Generate shell completion script")
public class CompletionsCommand implements Command<CommandInvocation> {

    @Argument(description = "Target shell: bash, zsh, fish, or pwsh")
    ShellType shell;

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            var builder = new AeshCommandContainerBuilder<>();
            var container = builder.create(PortsCli.class);

            if (shell == null) shell = detectShell();
            var generator = ShellCompletionGenerator.forShell(shell);
            String script = generator.generate(container.getParser(), "portz");

            // Print script to stdout so `portz completions zsh | source` works
            inv.print(script);

            // Usage hint to stderr so it doesn't pollute piped output
            String hint = switch (shell) {
                case BASH -> """
                        
                        # To use temporarily:
                        #   source <(ports completion bash)
                        # To install permanently:
                        #   ports completion bash > ~/.local/share/bash-completion/completions/ports
                        """;
                case ZSH -> """
                        
                        # To use temporarily:
                        #   source <(ports completion zsh)
                        # To install permanently:
                        #   ports completion zsh > ~/.zsh/completions/_ports && compinit
                        """;
                case FISH -> """
                        
                        # To use temporarily:
                        #   ports completion fish | source
                        # To install permanently:
                        #   ports completion fish > ~/.config/fish/completions/ports.fish
                        """;
                case PWSH -> """
                        
                        # To use temporarily:
                        #   ports completion pwsh | Invoke-Expression
                        # To install permanently:
                        #   ports completion pwsh >> $PROFILE
                        """;
            };
            System.err.print(hint);

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }

    private static ShellType detectShell() {
        String shell = System.getenv("SHELL");
        if (shell != null) {
            if (shell.endsWith("zsh")) return ShellType.ZSH;
            if (shell.endsWith("fish")) return ShellType.FISH;
        }
        // Windows: check PSModulePath (PowerShell sets it)
        if (System.getenv("PSModulePath") != null) return ShellType.PWSH;
        return ShellType.BASH;
    }
}
