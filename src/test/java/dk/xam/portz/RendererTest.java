package dk.xam.portz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RendererTest {

    private static ProcessInfo proc(String name) {
        return new ProcessInfo(1, name, name, name, "1m", 60, 0, 0,
                ProcessStatus.HEALTHY, null, null, null, null, null);
    }

    private static ProcessInfo proc(String binary, String command) {
        return new ProcessInfo(1, binary, binary, command, "1m", 60, 0, 0,
                ProcessStatus.HEALTHY, null, null, null, null, null);
    }

    @Test void nameOf_unixPath() {
        assertEquals("java", Renderer.nameOf(proc("/usr/bin/java")));
    }

    @Test void nameOf_windowsPath() {
        assertEquals("Dropbox.exe", Renderer.nameOf(proc("C:\\Program Files (x86)\\Dropbox\\Client\\Dropbox.exe")));
    }

    @Test void nameOf_windowsPathWithForwardSlash() {
        assertEquals("smartgit.exe", Renderer.nameOf(proc("C:/tools/SmartGit/bin/smartgit.exe")));
    }

    @Test void nameOf_simpleName() {
        assertEquals("node", Renderer.nameOf(proc("node")));
    }

    // --- compactPath tests ---

    @Test void compactPath_sdkman() {
        String home = System.getProperty("user.home");
        assertEquals("~/.s/c/j/2/b/java",
                Renderer.compactPath(Renderer.tildeHome(home + "/.sdkman/candidates/java/25.0.3-tem/bin/java")));
    }

    @Test void compactPath_windowsPath() {
        assertEquals("C\\P\\D\\C\\Dropbox.exe",
                Renderer.compactPath("C\\Program Files\\Dropbox\\Client\\Dropbox.exe"));
    }

    @Test void compactPath_simpleName() {
        assertEquals("java", Renderer.compactPath("java"));
    }

    @Test void compactPath_unixPath() {
        assertEquals("/u/b/java", Renderer.compactPath("/usr/bin/java"));
    }

    @Test void compactPath_dotDirs() {
        String home = System.getProperty("user.home");
        assertEquals("~/.c/b/node",
                Renderer.compactPath(Renderer.tildeHome(home + "/.config/bin/node")));
    }

    @Test void compactPath_singleDir() {
        assertEquals("/java", Renderer.compactPath("/java"));
    }

    @Test void compactPath_appBundle() {
        assertEquals("/A/G/C/M/Google Chrome",
                Renderer.compactPath("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
    }

    // --- compactCommand tests (binary compacted, args preserved) ---

    @Test void compactCommand_argsPreserved() {
        var p = proc("/usr/local/bin/python3", "/usr/local/bin/python3 -m http.server 8080");
        assertEquals("/u/l/b/python3 -m http.server 8080", Renderer.compactCommand(p));
    }

    @Test void compactCommand_jarPathInArgsNotCompacted() {
        String home = System.getProperty("user.home");
        var p = proc(home + "/.sdkman/candidates/java/25/bin/java",
                home + "/.sdkman/candidates/java/25/bin/java -jar /Users/max/code/project/target/quarkus-run.jar");
        assertEquals("~/.s/c/j/2/b/java -jar /Users/max/code/project/target/quarkus-run.jar",
                Renderer.compactCommand(p));
    }
}
