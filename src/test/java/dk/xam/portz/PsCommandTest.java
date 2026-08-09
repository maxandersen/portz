package dk.xam.portz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PsCommandTest {

    @Test void summarizeCommand_stripsPath() {
        assertEquals("java app.jar", PsCommand.summarizeCommand("/usr/bin/java -jar app.jar"));
    }

    @Test void summarizeCommand_skipsFlags() {
        assertEquals("node server.js", PsCommand.summarizeCommand("node --inspect server.js"));
    }

    @Test void summarizeCommand_maxThreeArgs() {
        assertEquals("python a b c", PsCommand.summarizeCommand("python a b c d e"));
    }

    @Test void summarizeCommand_binaryOnly() {
        assertEquals("nginx", PsCommand.summarizeCommand("/usr/sbin/nginx"));
    }

    @Test void summarizeCommand_empty() {
        assertEquals("", PsCommand.summarizeCommand(""));
    }
}
