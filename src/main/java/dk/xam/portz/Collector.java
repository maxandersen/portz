package dk.xam.portz;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class Collector {

    public static List<PortEntry> collectAll(boolean showAll) throws Exception {
        // Collect ports and docker concurrently
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var portsFuture = exec.submit(Platform::collectListeningPortEntries);
            var dockerFuture = exec.submit(DockerInfo::collectContainers);

            var rawPorts = portsFuture.get();
            var dockerContainers = dockerFuture.get();

            if (rawPorts.isEmpty()) return List.of();

            // Collect PIDs
            var pids = new LinkedHashSet<Long>();
            rawPorts.forEach(rp -> pids.add(rp.pid()));

            // Collect process info and CWDs concurrently
            var infoFuture = exec.submit(() -> Platform.collectProcessInfo(pids));
            var cwdFuture = exec.submit(() -> Platform.collectProcessCwds(pids));

            var processData = infoFuture.get();
            var cwdMap = cwdFuture.get();

            // Enrich: cmdline, framework, git branch — concurrently per process
            var enriched = new ConcurrentHashMap<Long, ProcessInfo>();
            var enrichTasks = new ArrayList<Future<?>>();

            for (var entry : processData.entrySet()) {
                long pid = entry.getKey();
                var data = entry.getValue();
                enrichTasks.add(exec.submit(() -> {
                    var status = Platform.parseStatus(data.stat(), data.ppid());
                    long uptimeSeconds = Platform.parseUptime(data.etime());
                    String cmdline = Platform.getProcessCmdline(pid);
                    if (cmdline.isEmpty()) cmdline = data.name();
                    // Derive a better display name from the full cmdline (ps comm truncates to 16 chars)
                    String displayName = extractDisplayName(cmdline, data.name());

                    String cwd = cwdMap.get(pid);
                    String projectName = null;
                    if (cwd != null) {
                        var fn = Path.of(cwd).getFileName();
                        if (fn != null) projectName = fn.toString();
                    }
                    Framework framework = FrameworkDetector.detect(cwd, cmdline);
                    String gitBranch = cwd != null ? Platform.getGitBranch(cwd) : null;

                    enriched.put(pid, new ProcessInfo(
                            pid, displayName, cmdline,
                            Platform.formatUptime(uptimeSeconds), uptimeSeconds,
                            data.rss(), data.ppid(), status,
                            cwd, projectName, framework, gitBranch
                    ));
                }));
            }
            for (var f : enrichTasks) f.get(); // wait all

            // Build port entries
            var entries = new ArrayList<PortEntry>();
            for (var rp : rawPorts) {
                var proc = enriched.get(rp.pid());
                if (proc == null) continue;

                // Docker service override
                var container = DockerInfo.findForPort(dockerContainers, rp.port());
                if (container != null) {
                    String service = container.detectService();
                    if (service != null) {
                        proc = proc.withFramework(Framework.unknown("Docker · " + service));
                    }
                }

                if (showAll || (proc.isDevProcess() && !proc.isSystemProcess())) {
                    entries.add(new PortEntry(rp.port(), rp.pid(), rp.address(), proc));
                }
            }
            entries.sort(Comparator.comparingInt(PortEntry::port));
            return entries;
        }
    }

    /** Extract a human-friendly process name from the full cmdline. */
    private static String extractDisplayName(String cmdline, String fallback) {
        // For .app bundles, detect before splitting — binary path may contain spaces
        // e.g. /Applications/Google Chrome.app/Contents/MacOS/Google Chrome --flag
        int appIdx = cmdline.indexOf(".app/");
        if (appIdx > 0) {
            String appPart = cmdline.substring(0, appIdx);
            int slash = appPart.lastIndexOf('/');
            return slash >= 0 ? appPart.substring(slash + 1) : appPart;
        }
        // First token is the binary path/name
        String bin = cmdline.split("\\s+")[0];
        if (bin.contains("/")) {
            int slash = bin.lastIndexOf('/');
            return slash >= 0 ? bin.substring(slash + 1) : bin;
        }
        return bin.isEmpty() ? fallback : bin;
    }
}
