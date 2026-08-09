package dk.xam.portz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreprocessArgsTest {

    @Test void barePortNumber() {
        assertArrayEquals(new String[]{"--port=8080"}, PortzRunnerFactory.preprocessArgs(new String[]{"8080"}));
    }

    @Test void portWithLeadingOptions() {
        assertArrayEquals(new String[]{"--all", "--port=3000"},
                PortzRunnerFactory.preprocessArgs(new String[]{"--all", "3000"}));
    }

    @Test void subcommandPassesThrough() {
        assertArrayEquals(new String[]{"ps"}, PortzRunnerFactory.preprocessArgs(new String[]{"ps"}));
    }

    @Test void subcommandWithOptions() {
        assertArrayEquals(new String[]{"ps", "--all"},
                PortzRunnerFactory.preprocessArgs(new String[]{"ps", "--all"}));
    }

    @Test void noArgs() {
        assertArrayEquals(new String[]{}, PortzRunnerFactory.preprocessArgs(new String[]{}));
    }

    @Test void nullArgs() {
        assertNull(PortzRunnerFactory.preprocessArgs(null));
    }

    @Test void nonNumericNonSubcommand() {
        // Unknown arg, not a number — passes through unchanged
        assertArrayEquals(new String[]{"foo"}, PortzRunnerFactory.preprocessArgs(new String[]{"foo"}));
    }

    @Test void watchPassesThrough() {
        assertArrayEquals(new String[]{"watch"}, PortzRunnerFactory.preprocessArgs(new String[]{"watch"}));
    }

    @Test void completionPassesThrough() {
        assertArrayEquals(new String[]{"completion", "zsh"},
                PortzRunnerFactory.preprocessArgs(new String[]{"completion", "zsh"}));
    }
}
