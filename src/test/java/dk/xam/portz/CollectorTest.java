package dk.xam.portz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectorTest {

    @Test void extractDisplayName_javaPath() {
        assertEquals("java", Collector.extractDisplayName(
                "/Users/max/.sdkman/candidates/java/current/bin/java -jar app.jar", "fallback"));
    }

    @Test void extractDisplayName_appBundle() {
        assertEquals("Google Chrome", Collector.extractDisplayName(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome --flag", "fallback"));
    }

    @Test void extractDisplayName_vscodeBundle() {
        assertEquals("Visual Studio Code", Collector.extractDisplayName(
                "/Applications/Visual Studio Code.app/Contents/MacOS/Electron --type=renderer", "fallback"));
    }

    @Test void extractDisplayName_simpleBinary() {
        assertEquals("node", Collector.extractDisplayName("node server.js", "fallback"));
    }

    @Test void extractDisplayName_emptyUsesFallback() {
        assertEquals("fallback", Collector.extractDisplayName("", "fallback"));
    }
}
