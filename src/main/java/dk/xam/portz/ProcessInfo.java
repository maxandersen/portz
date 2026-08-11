package dk.xam.portz;

import java.util.Set;

public record ProcessInfo(
        long pid, String name, String commandBinary, String command, String uptime, long uptimeSeconds,
        long memoryKb, long ppid, ProcessStatus status,
        String cwd, String projectName, Runtime runtime, Framework framework, String gitBranch
) {
    private static final Set<String> DEV_RUNTIMES = Set.of(
            "node", "python", "python3", "ruby", "go", "cargo", "java", "javac", "mvn", "gradle",
            "npm", "yarn", "pnpm", "bun", "deno", "php", "elixir", "mix", "dotnet", "rails",
            "puma", "uvicorn", "gunicorn"
    );

    private static final Set<String> SYSTEM_APPS = Set.of(
            "Spotify", "Raycast", "Slack", "Discord", "Electron", "Google Chrome",
            "Safari", "Firefox", "systemd", "launchd", "cron", "sshd", "httpd"
    );

    // IDE processes whose children (LSP servers etc.) are tooling, not user dev projects
    private static final Set<String> IDE_PARENTS = Set.of(
            "Code Helper", "Code Helper (Plugin)", "Electron",
            "idea", "phpstorm", "webstorm", "goland", "rider", "clion", "rubymine", "pycharm"
    );

    public boolean isDevProcess() {
        String lower = name.toLowerCase();
        return DEV_RUNTIMES.stream().anyMatch(lower::contains) || framework != null || runtime != null;
    }

    public boolean isSystemProcess() {
        return SYSTEM_APPS.stream().anyMatch(name::contains);
    }

    /** True if this process is an IDE child (LSP server, language tooling) without a detected framework. */
    public boolean isIdeTooling() {
        // IDE-spawned process with a real framework (e.g. mvn quarkus:dev from terminal) → keep it
        if (framework != null) return false;
        return ProcessHandle.of(ppid)
                .flatMap(ph -> ph.info().command())
                .map(c -> {
                    int sep = Math.max(c.lastIndexOf('/'), c.lastIndexOf('\\'));
                    String parentName = sep >= 0 ? c.substring(sep + 1) : c;
                    return IDE_PARENTS.stream().anyMatch(parentName::contains);
                })
                .orElse(false);
    }

    public double memoryMb() { return memoryKb / 1024.0; }

    /** Display: "☕ Java · 🔮 Quarkus" or "☕ Java" or "-" */
    public String runtimeFrameworkDisplay() {
        if (runtime != null && framework != null)
            return framework.emoji() + " " + framework.displayName();
        if (framework != null) return framework.emoji() + " " + framework.displayName();
        if (runtime != null) return runtime.emoji() + " " + runtime.displayName();
        return "-";
    }

    public ProcessInfo withEnrichment(String cwd, String projectName, Runtime runtime, Framework framework, String gitBranch) {
        return new ProcessInfo(pid, name, commandBinary, command, uptime, uptimeSeconds, memoryKb, ppid, status,
                cwd, projectName, runtime, framework, gitBranch);
    }

    public ProcessInfo withFramework(Framework framework) {
        return new ProcessInfo(pid, name, commandBinary, command, uptime, uptimeSeconds, memoryKb, ppid, status,
                cwd, projectName, runtime, framework, gitBranch);
    }
}
