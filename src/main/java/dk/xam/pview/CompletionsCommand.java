package dk.xam.pview;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.util.completer.ShellCompletionGenerator;
import org.aesh.util.completer.ShellCompletionGenerator.ShellType;

@CommandDefinition(name = "completions", description = "Generate shell completion script")
public class CompletionsCommand implements Command<CommandInvocation> {

    @Argument(description = "Target shell: bash, zsh, fish, or pwsh", defaultValue = {"bash"})
    ShellType shell;

    @Override
    public CommandResult execute(CommandInvocation inv) {
        try {
            var builder = new AeshCommandContainerBuilder<>();
            var container = builder.create(PortsCli.class);

            var generator = ShellCompletionGenerator.forShell(shell);
            String script = generator.generate(container.getParser(), "ports");

            // Print script to stdout so `ports completions zsh | source` works
            inv.print(script);

            // Usage hint to stderr so it doesn't pollute piped output
            String hint = switch (shell) {
                case BASH -> """
                        
                        # To use temporarily:
                        #   source <(ports completions bash)
                        # To install permanently:
                        #   ports completions bash > ~/.local/share/bash-completion/completions/ports
                        """;
                case ZSH -> """
                        
                        # To use temporarily:
                        #   source <(ports completions zsh)
                        # To install permanently:
                        #   ports completions zsh > ~/.zsh/completions/_ports && compinit
                        """;
                case FISH -> """
                        
                        # To use temporarily:
                        #   ports completions fish | source
                        # To install permanently:
                        #   ports completions fish > ~/.config/fish/completions/ports.fish
                        """;
                case PWSH -> """
                        
                        # To use temporarily:
                        #   ports completions pwsh | Invoke-Expression
                        # To install permanently:
                        #   ports completions pwsh >> $PROFILE
                        """;
            };
            System.err.print(hint);

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            inv.println(Ansi.markup("[red]Error: %s[/]".formatted(e.getMessage())));
            return CommandResult.FAILURE;
        }
    }
}
