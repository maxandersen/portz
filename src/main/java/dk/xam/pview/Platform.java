package dk.xam.pview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Platform layer — shells out to lsof/ps/kill on Unix.
 * ponytail: macOS/Linux only for now, Windows later if needed.
 */
public class Platform {

    record RawPort(long pid, int port, String address) {}

    static List<RawPort> collectListeningPortEntries() throws Exception {
        var pb = new ProcessBuilder("lsof", "-iTCP", "-sTCP:LISTEN", "-P", "-n");
        pb.redirectErrorStream(false);
        var proc = pb.start();
        String stdout = new String(proc.getInputStream().readAllBytes());
        proc.waitFor();

        var results = new ArrayList<RawPort>();
        for (String line : stdout.lines().skip(1).toList()) {
            String[] parts = line.split("\\s+");
            if (parts.length < 9) continue;
            try {
                long pid = Long.parseLong(parts[1]);
                String name = parts[8];
                int lastColon = name.lastIndexOf(':');
                if (lastColon < 0) continue;
                String address = name.substring(0, lastColon);
                int port = Integer.parseInt(name.substring(lastColon + 1));
                results.add(new RawPort(pid, port, address));
            } catch (NumberFormatException ignored) {}
        }
        return results;
    }

    record ProcessData(long pid, String name, String etime, long rss, long ppid, String stat) {}

    static Map<Long, ProcessData> collectProcessInfo(Set<Long> pids) throws Exception {
        if (pids.isEmpty()) return Map.of();
        String pidList = String.join(",", pids.stream().map(String::valueOf).toList());

        var pb = new ProcessBuilder("ps", "-o", "pid,comm,etime,rss,ppid,stat", "-p", pidList);
        var proc = pb.start();
        String stdout = new String(proc.getInputStream().readAllBytes());
        proc.waitFor();

        var map = new HashMap<Long, ProcessData>();
        for (String line : stdout.lines().skip(1).toList()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) continue;
            try {
                long pid = Long.parseLong(parts[0]);
                map.put(pid, new ProcessData(
                        pid, parts[1], parts[2],
                        Long.parseLong(parts[3]),
                        Long.parseLong(parts[4]),
                        parts[5]
                ));
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    static Map<Long, String> collectProcessCwds(Set<Long> pids) throws Exception {
        if (pids.isEmpty()) return Map.of();
        String pidList = String.join(",", pids.stream().map(String::valueOf).toList());

        var pb = new ProcessBuilder("lsof", "-d", "cwd", "-a", "-p", pidList, "-Fn");
        var proc = pb.start();
        String stdout = new String(proc.getInputStream().readAllBytes());
        proc.waitFor();

        var map = new HashMap<Long, String>();
        Long currentPid = null;
        for (String line : stdout.lines().toList()) {
            if (line.startsWith("p")) {
                try { currentPid = Long.parseLong(line.substring(1)); } catch (NumberFormatException e) { currentPid = null; }
            } else if (line.startsWith("n") && currentPid != null) {
                map.put(currentPid, line.substring(1));
            }
        }
        return map;
    }

    static String getProcessCmdline(long pid) {
        // Try /proc first (Linux)
        try {
            Path procPath = Path.of("/proc/" + pid + "/cmdline");
            if (Files.exists(procPath)) {
                String content = Files.readString(procPath).replace('\0', ' ').trim();
                if (!content.isEmpty()) return content;
            }
        } catch (Exception ignored) {}

        // Fallback to ps
        try {
            var pb = new ProcessBuilder("ps", "-p", String.valueOf(pid), "-o", "command=");
            var proc = pb.start();
            String result = new String(proc.getInputStream().readAllBytes()).trim();
            proc.waitFor();
            return result;
        } catch (Exception e) {
            return "";
        }
    }

    static ProcessStatus parseStatus(String stat, long ppid) {
        if (stat.contains("Z")) return ProcessStatus.ZOMBIE;
        if (ppid == 1) return ProcessStatus.ORPHANED;
        return ProcessStatus.HEALTHY;
    }

    static long parseUptime(String etime) {
        // Formats: MM:SS, HH:MM:SS, D-HH:MM:SS
        String[] parts = etime.split("[\\-:]");
        return switch (parts.length) {
            case 2 -> parseLong(parts[0]) * 60 + parseLong(parts[1]);
            case 3 -> parseLong(parts[0]) * 3600 + parseLong(parts[1]) * 60 + parseLong(parts[2]);
            case 4 -> parseLong(parts[0]) * 86400 + parseLong(parts[1]) * 3600 + parseLong(parts[2]) * 60 + parseLong(parts[3]);
            default -> 0;
        };
    }

    static String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) return "%dd %dh".formatted(days, hours);
        if (hours > 0) return "%dh %dm".formatted(hours, minutes);
        if (minutes > 0) return "%dm %ds".formatted(minutes, secs);
        return "%ds".formatted(secs);
    }

    static String getGitBranch(String cwd) {
        try {
            var pb = new ProcessBuilder("git", "-C", cwd, "branch", "--show-current");
            pb.redirectErrorStream(false);
            var proc = pb.start();
            String result = new String(proc.getInputStream().readAllBytes()).trim();
            return proc.waitFor() == 0 && !result.isEmpty() ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void killGraceful(long pid) {
        try {
            new ProcessBuilder("kill", "-TERM", String.valueOf(pid)).start().waitFor();
            Thread.sleep(3000);
            if (isAlive(pid)) {
                new ProcessBuilder("kill", "-KILL", String.valueOf(pid)).start().waitFor();
            }
            System.out.println(Ansi.markup("  [green]✓[/] PID " + pid));
        } catch (Exception e) {
            System.err.println(Ansi.markup("[red]Failed to kill PID " + pid + ": " + e.getMessage() + "[/]"));
        }
    }

    static boolean isAlive(long pid) {
        try {
            var proc = new ProcessBuilder("ps", "-p", String.valueOf(pid)).start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}
