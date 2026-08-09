package dk.xam.portz;

import java.util.Set;

public record ProcessInfo(
        long pid, String name, String command, String uptime, long uptimeSeconds,
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

    public boolean isDevProcess() {
        String lower = name.toLowerCase();
        return DEV_RUNTIMES.stream().anyMatch(lower::contains) || framework != null || runtime != null;
    }

    public boolean isSystemProcess() {
        return SYSTEM_APPS.stream().anyMatch(name::contains);
    }

    public double memoryMb() { return memoryKb / 1024.0; }

    /** Display: "☕ Java · 🔮 Quarkus" or "☕ Java" or "-" */
    public String runtimeFrameworkDisplay() {
        if (runtime != null && framework != null)
            return runtime.emoji() + " " + runtime.displayName() + " · " + framework.emoji() + " " + framework.displayName();
        if (framework != null) return framework.emoji() + " " + framework.displayName();
        if (runtime != null) return runtime.emoji() + " " + runtime.displayName();
        return "-";
    }

    public ProcessInfo withEnrichment(String cwd, String projectName, Runtime runtime, Framework framework, String gitBranch) {
        return new ProcessInfo(pid, name, command, uptime, uptimeSeconds, memoryKb, ppid, status,
                cwd, projectName, runtime, framework, gitBranch);
    }

    public ProcessInfo withFramework(Framework framework) {
        return new ProcessInfo(pid, name, command, uptime, uptimeSeconds, memoryKb, ppid, status,
                cwd, projectName, runtime, framework, gitBranch);
    }
}
