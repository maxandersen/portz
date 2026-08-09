package dk.xam.pview;

import java.util.Set;

public record ProcessInfo(
        long pid,
        String name,
        String command,
        String uptime,
        long uptimeSeconds,
        long memoryKb,
        long ppid,
        ProcessStatus status,
        String cwd,
        String projectName,
        Framework framework,
        String gitBranch
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
        return DEV_RUNTIMES.stream().anyMatch(lower::contains) || framework != null;
    }

    public boolean isSystemProcess() {
        return SYSTEM_APPS.stream().anyMatch(name::contains);
    }

    public double memoryMb() {
        return memoryKb / 1024.0;
    }

    /** Return a copy with enrichment fields set. */
    public ProcessInfo withEnrichment(String cwd, String projectName, Framework framework, String gitBranch) {
        return new ProcessInfo(pid, name, command, uptime, uptimeSeconds, memoryKb, ppid, status,
                cwd, projectName, framework, gitBranch);
    }

    public ProcessInfo withCommand(String command) {
        return new ProcessInfo(pid, name, command, uptime, uptimeSeconds, memoryKb, ppid, status,
                cwd, projectName, framework, gitBranch);
    }

    public ProcessInfo withFramework(Framework framework) {
        return new ProcessInfo(pid, name, command, uptime, uptimeSeconds, memoryKb, ppid, status,
                cwd, projectName, framework, gitBranch);
    }
}
