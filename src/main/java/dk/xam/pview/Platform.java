package dk.xam.pview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Platform abstraction — delegates to Unix (lsof/ps/kill) or Windows (netstat/taskkill/ProcessHandle)
 * based on OS detection at startup.
 */
public class Platform {

    static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    record RawPort(long pid, int port, String address) {}
    record ProcessData(long pid, String name, String etime, long rss, long ppid, String stat) {}

    // === Port collection ===

    static List<RawPort> collectListeningPortEntries() throws Exception {
        return IS_WINDOWS ? collectPortsWindows() : collectPortsUnix();
    }

    private static List<RawPort> collectPortsUnix() throws Exception {
        String stdout = exec("lsof", "-iTCP", "-sTCP:LISTEN", "-P", "-n");
        var results = new ArrayList<RawPort>();
        for (String line : stdout.lines().skip(1).toList()) {
            String[] parts = line.split("\\s+");
            if (parts.length < 9) continue;
            try {
                long pid = Long.parseLong(parts[1]);
                String name = parts[8];
                int lastColon = name.lastIndexOf(':');
                if (lastColon < 0) continue;
                results.add(new RawPort(pid, Integer.parseInt(name.substring(lastColon + 1)), name.substring(0, lastColon)));
            } catch (NumberFormatException _) {}
        }
        return results;
    }

    private static List<RawPort> collectPortsWindows() throws Exception {
        // netstat -ano -p TCP: TCP  0.0.0.0:PORT  0.0.0.0:0  LISTENING  PID
        String stdout = exec("netstat", "-ano", "-p", "TCP");
        var results = new ArrayList<RawPort>();
        for (String line : stdout.lines().toList()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 5 && "TCP".equals(parts[0]) && "LISTENING".equals(parts[3])) {
                int lastColon = parts[1].lastIndexOf(':');
                if (lastColon < 0) continue;
                try {
                    results.add(new RawPort(
                            Long.parseLong(parts[4]),
                            Integer.parseInt(parts[1].substring(lastColon + 1)),
                            parts[1].substring(0, lastColon)));
                } catch (NumberFormatException _) {}
            }
        }
        return results;
    }

    // === Process info ===

    static Map<Long, ProcessData> collectProcessInfo(Set<Long> pids) throws Exception {
        return IS_WINDOWS ? collectProcessInfoWindows(pids) : collectProcessInfoUnix(pids);
    }

    private static Map<Long, ProcessData> collectProcessInfoUnix(Set<Long> pids) throws Exception {
        if (pids.isEmpty()) return Map.of();
        String stdout = exec("ps", "-o", "pid,comm,etime,rss,ppid,stat", "-p",
                String.join(",", pids.stream().map(String::valueOf).toList()));

        var map = new HashMap<Long, ProcessData>();
        for (String line : stdout.lines().skip(1).toList()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) continue;
            try {
                long pid = Long.parseLong(parts[0]);
                map.put(pid, new ProcessData(pid, parts[1], parts[2],
                        Long.parseLong(parts[3]), Long.parseLong(parts[4]), parts[5]));
            } catch (NumberFormatException _) {}
        }
        return map;
    }

    private static Map<Long, ProcessData> collectProcessInfoWindows(Set<Long> pids) {
        var map = new HashMap<Long, ProcessData>();
        for (long pid : pids) {
            ProcessHandle.of(pid).ifPresent(ph -> {
                var info = ph.info();
                String name = info.command().map(c -> Path.of(c).getFileName().toString()).orElse("unknown");
                long ppid = ph.parent().map(ProcessHandle::pid).orElse(0L);
                long uptimeSeconds = info.startInstant()
                        .map(start -> Duration.between(start, Instant.now()).getSeconds())
                        .orElse(0L);
                map.put(pid, new ProcessData(pid, name, formatEtime(uptimeSeconds), 0, ppid,
                        ph.isAlive() ? "R" : "Z"));
            });
        }
        enrichWindowsRss(map);
        return map;
    }

    /** Parse tasklist /FO CSV to get memory (KB) per PID. */
    private static void enrichWindowsRss(Map<Long, ProcessData> map) {
        if (map.isEmpty()) return;
        try {
            String stdout = exec("tasklist", "/FO", "CSV", "/NH");
            // "name.exe","PID","Session Name","Session#","Mem Usage"
            for (String line : stdout.lines().toList()) {
                String[] fields = line.split("\",\"");
                if (fields.length >= 5) {
                    try {
                        long pid = Long.parseLong(fields[1].replace("\"", "").trim());
                        if (map.containsKey(pid)) {
                            long rss = Long.parseLong(fields[4].replace("\"", "").replace(",", "").replace(" K", "").trim());
                            var old = map.get(pid);
                            map.put(pid, new ProcessData(old.pid(), old.name(), old.etime(), rss, old.ppid(), old.stat()));
                        }
                    } catch (NumberFormatException _) {}
                }
            }
        } catch (Exception _) {}
    }

    private static String formatEtime(long seconds) {
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (d > 0) return "%d-%02d:%02d:%02d".formatted(d, h, m, s);
        if (h > 0) return "%d:%02d:%02d".formatted(h, m, s);
        return "%02d:%02d".formatted(m, s);
    }

    // === CWDs ===

    static Map<Long, String> collectProcessCwds(Set<Long> pids) throws Exception {
        if (IS_WINDOWS) return collectCwdsWindows(pids);
        if (pids.isEmpty()) return Map.of();

        String stdout = exec("lsof", "-d", "cwd", "-a", "-p",
                String.join(",", pids.stream().map(String::valueOf).toList()), "-Fn");

        var map = new HashMap<Long, String>();
        Long currentPid = null;
        for (String line : stdout.lines().toList()) {
            if (line.startsWith("p")) {
                try { currentPid = Long.parseLong(line.substring(1)); } catch (NumberFormatException _) { currentPid = null; }
            } else if (line.startsWith("n") && currentPid != null) {
                map.put(currentPid, line.substring(1));
            }
        }
        return map;
    }

    private static Map<Long, String> collectCwdsWindows(Set<Long> pids) {
        if (pids.isEmpty()) return Map.of();
        var map = new HashMap<Long, String>();
        // ponytail: ProcessHandle doesn't expose CWD on Windows.
        // Use PowerShell to read it via .NET Process class. WorkingDirectory is
        // usually empty, so fall back to the exe's directory.
        try {
            String pidFilter = String.join(",", pids.stream().map(String::valueOf).toList());
            String script = "$pids = @(%s); foreach ($id in $pids) { try { $p = [System.Diagnostics.Process]::GetProcessById($id); $cwd = $p.StartInfo.WorkingDirectory; if (-not $cwd) { $cwd = [IO.Path]::GetDirectoryName($p.MainModule.FileName) }; Write-Output \"$id|$cwd\" } catch {} }"
                    .formatted(pidFilter);
            String stdout = exec("powershell", "-NoProfile", "-Command", script);
            for (String line : stdout.lines().toList()) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    try { map.put(Long.parseLong(parts[0].trim()), parts[1].trim()); }
                    catch (NumberFormatException _) {}
                }
            }
        } catch (Exception _) {}
        return map;
    }

    // === Cmdline ===

    static String getProcessCmdline(long pid) {
        if (IS_WINDOWS) {
            return ProcessHandle.of(pid)
                    .flatMap(ph -> ph.info().commandLine())
                    .or(() -> ProcessHandle.of(pid).flatMap(ph -> ph.info().command()))
                    .orElse("");
        }
        // Try /proc first (Linux)
        try {
            Path procPath = Path.of("/proc/" + pid + "/cmdline");
            if (Files.exists(procPath)) {
                String content = Files.readString(procPath).replace('\0', ' ').trim();
                if (!content.isEmpty()) return content;
            }
        } catch (Exception _) {}
        // Fallback to ps (macOS/Linux)
        try { return exec("ps", "-p", String.valueOf(pid), "-o", "command=").trim(); }
        catch (Exception _) { return ""; }
    }

    // === Status ===

    static ProcessStatus parseStatus(String stat, long ppid) {
        if (stat.contains("Z")) return ProcessStatus.ZOMBIE;
        if (IS_WINDOWS ? (ppid == 0 || ppid == 4) : ppid == 1) return ProcessStatus.ORPHANED;
        return ProcessStatus.HEALTHY;
    }

    // === Uptime ===

    static long parseUptime(String etime) {
        String[] parts = etime.split("[\\-:]");
        return switch (parts.length) {
            case 2 -> parseLong(parts[0]) * 60 + parseLong(parts[1]);
            case 3 -> parseLong(parts[0]) * 3600 + parseLong(parts[1]) * 60 + parseLong(parts[2]);
            case 4 -> parseLong(parts[0]) * 86400 + parseLong(parts[1]) * 3600 + parseLong(parts[2]) * 60 + parseLong(parts[3]);
            default -> 0;
        };
    }

    static String formatUptime(long seconds) {
        long days = seconds / 86400, hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60, secs = seconds % 60;
        if (days > 0) return "%dd %dh".formatted(days, hours);
        if (hours > 0) return "%dh %dm".formatted(hours, minutes);
        if (minutes > 0) return "%dm %ds".formatted(minutes, secs);
        return "%ds".formatted(secs);
    }

    // === Git ===

    static String getGitBranch(String cwd) {
        try {
            var pb = new ProcessBuilder("git", "-C", cwd, "branch", "--show-current");
            var proc = pb.start();
            String result = new String(proc.getInputStream().readAllBytes()).trim();
            return proc.waitFor() == 0 && !result.isEmpty() ? result : null;
        } catch (Exception _) { return null; }
    }

    // === Kill ===

    public static void killGraceful(long pid) {
        try {
            if (IS_WINDOWS) {
                new ProcessBuilder("taskkill", "/PID", String.valueOf(pid)).start().waitFor();
                Thread.sleep(3000);
                if (isAlive(pid)) {
                    new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid)).start().waitFor();
                }
            } else {
                new ProcessBuilder("kill", "-TERM", String.valueOf(pid)).start().waitFor();
                Thread.sleep(3000);
                if (isAlive(pid)) {
                    new ProcessBuilder("kill", "-KILL", String.valueOf(pid)).start().waitFor();
                }
            }
            System.out.println(Ansi.markup("  [green]✓[/] PID %d".formatted(pid)));
        } catch (Exception e) {
            System.err.println(Ansi.markup("[red]Failed to kill PID %d: %s[/]".formatted(pid, e.getMessage())));
        }
    }

    static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    // === CPU ===

    static Map<Long, Double> getCpuSample(String pidList) throws Exception {
        if (IS_WINDOWS) return getCpuSampleWindows(pidList);

        String stdout = exec("ps", "-o", "pid,%cpu", "-p", pidList);
        var map = new HashMap<Long, Double>();
        for (String line : stdout.lines().skip(1).toList()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                try { map.put(Long.parseLong(parts[0]), Double.parseDouble(parts[1])); }
                catch (NumberFormatException _) {}
            }
        }
        return map;
    }

    private static Map<Long, Double> getCpuSampleWindows(String pidList) {
        // ponytail: wmic is deprecated but universal. PowerShell is slower.
        var pidSet = Set.of(pidList.split(","));
        var map = new HashMap<Long, Double>();
        try {
            String stdout = exec("wmic", "path", "Win32_PerfFormattedData_PerfProc_Process",
                    "get", "IDProcess,PercentProcessorTime", "/format:csv");
            for (String line : stdout.lines().toList()) {
                String[] fields = line.split(",");
                if (fields.length >= 3) {
                    try {
                        String pidStr = fields[1].trim();
                        if (pidSet.contains(pidStr)) {
                            map.put(Long.parseLong(pidStr), Double.parseDouble(fields[2].trim()));
                        }
                    } catch (NumberFormatException _) {}
                }
            }
        } catch (Exception _) {}
        return map;
    }

    // === Helpers ===

    /** Run a command and return stdout. */
    static String exec(String... cmd) throws Exception {
        var proc = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        String stdout = new String(proc.getInputStream().readAllBytes());
        proc.waitFor();
        return stdout;
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException _) { return 0; }
    }
}
