package dk.xam.portz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnsiTest {

    @Test void markupProducesAnsi() {
        String result = Ansi.markup("[bold]hello[/]");
        assertTrue(result.contains("hello"));
        // Should contain ANSI escape if NO_COLOR is not set
        if (!Ansi.NO_COLOR) {
            assertTrue(result.contains("\033["), "Expected ANSI escapes");
        }
    }

    @Test void markupPlainText() {
        String result = Ansi.markup("no tags here");
        assertEquals("no tags here", result);
    }

    @Test void markupEscapedBrackets() {
        String result = Ansi.markup("[[y/N]]");
        assertTrue(result.contains("[y/N]"));
    }
}
