package dk.xam.pview;

import org.aesh.command.invocation.CommandInvocation;
import org.aesh.terminal.tty.Size;
import org.aesh.util.table.Table;
import org.aesh.util.table.TableStyle;

import java.util.List;

public class Renderer {

    private static final String HOME = System.getProperty("user.home");

    public static void renderPortsTable(List<PortEntry> entries, boolean showAll, CommandInvocation inv) {
        if (entries.isEmpty()) {
            Ansi.println(inv, Ansi.yellow("No listening ports found."));
            return;
        }

        int width = getTerminalWidth(inv);

        String output = Table.<PortEntry>builder()
                .maxWidth(width)
                .style(TableStyle.DUCKDB)
                .column("PORT", e -> ":" + e.port())
                .column("NAME", e -> shortenProcessName(e.process().name()))
                .column("PID", e -> String.valueOf(e.pid()))
                .column("COMMAND", e -> truncateCommand(tildeHome(e.process().command()), width))
                .column("PROJECT", e -> e.process().projectName() != null ? e.process().projectName() : "-")
                .column("FRAMEWORK", e -> e.process().framework() != null
                        ? e.process().framework().emoji() + " " + e.process().framework().displayName()
                        : "-")
                .column("UPTIME", e -> e.process().uptime())
                .column("STATUS", e -> e.process().status().rawSymbol())
                .build()
                .render(entries);

        inv.print(output);

        // Footer
        String filter = showAll ? "" : " · " + Ansi.dim("--all to show everything");
        inv.println(String.format("%s %s active%s",
                Ansi.cyan(String.valueOf(entries.size())),
                entries.size() == 1 ? "port" : "ports",
                filter));
        inv.println("Run " + Ansi.dim("ports <number>") + " for details");
    }

    public static void renderOrphanTable(List<PortEntry> orphans, CommandInvocation inv) {
        int width = getTerminalWidth(inv);

        String output = Table.<PortEntry>builder()
                .maxWidth(width)
                .style(TableStyle.DUCKDB)
                .column("PID", e -> String.valueOf(e.pid()))
                .column("PROCESS", e -> e.process().name())
                .column("PROJECT", e -> e.process().projectName() != null ? e.process().projectName() : "-")
                .column("UPTIME", e -> e.process().uptime())
                .column("STATUS", e -> e.process().status().rawSymbol())
                .build()
                .render(orphans);

        inv.print(output);
    }

    /**
     * Truncate command to fit. Reserves ~40% of terminal width for COMMAND,
     * collapses path segments from left before hard-truncating.
     */
    private static String truncateCommand(String cmd, int termWidth) {
        int maxLen = termWidth > 0 ? Math.max(30, (termWidth * 40) / 100) : 60;
        if (cmd.length() <= maxLen) return cmd;

        // Split into binary path (first token) and args
        int space = cmd.indexOf(' ');
        String path = space > 0 ? cmd.substring(0, space) : cmd;
        String args = space > 0 ? cmd.substring(space) : "";

        // Collapse path segments from left: /Users/max/.sdkman/candidates -> /U/m/.s/c
        if (path.contains("/")) {
            String[] segs = path.split("/");
            for (int i = 1; i < segs.length - 1; i++) {
                if (segs[i].length() > 1) segs[i] = segs[i].substring(0, 1);
                String collapsed = String.join("/", segs) + args;
                if (collapsed.length() <= maxLen) return collapsed;
            }
            path = String.join("/", segs);
        }

        String result = path + args;
        return result.length() <= maxLen ? result : result.substring(0, maxLen - 1) + "…";
    }

    private static String tildeHome(String s) {
        return HOME != null && s.startsWith(HOME) ? "~" + s.substring(HOME.length()) : s;
    }

    /** Turn /Applications/Google Chrome.app/Contents/MacOS/Google Chrome -> Google Chrome */
    private static String shortenProcessName(String name) {
        if (name.contains("/")) {
            int appIdx = name.indexOf(".app/");
            if (appIdx > 0) {
                String appPart = name.substring(0, appIdx);
                int slash = appPart.lastIndexOf('/');
                return slash >= 0 ? appPart.substring(slash + 1) : appPart;
            }
            int slash = name.lastIndexOf('/');
            return slash >= 0 ? name.substring(slash + 1) : name;
        }
        return name;
    }

    static int getTerminalWidth(CommandInvocation inv) {
        int detected = 0;
        // Try aesh shell first
        try {
            Size size = inv.getShell().size();
            if (size != null && size.getWidth() > 0) detected = size.getWidth();
        } catch (Exception ignored) {}
        // Try stty via /dev/tty
        if (detected == 0) {
            try {
                var proc = new ProcessBuilder("stty", "size")
                        .redirectInput(new java.io.File("/dev/tty")).start();
                String out = new String(proc.getInputStream().readAllBytes()).trim();
                proc.waitFor();
                String[] parts = out.split("\\s+");
                if (parts.length >= 2) detected = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
        }
        // Try COLUMNS env var
        if (detected == 0) {
            try {
                String cols = System.getenv("COLUMNS");
                if (cols != null) detected = Integer.parseInt(cols);
            } catch (Exception ignored) {}
        }
        // ponytail: 80 is the universal "I don't actually know" default from tput/stty.
        // A real wide terminal reports its actual width. Treat <=80 as unknown
        // and return 0 (unlimited) so aesh Table sizes to content.
        return detected > 80 ? detected : 0;
    }
}
