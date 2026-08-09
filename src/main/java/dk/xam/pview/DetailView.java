package dk.xam.pview;

import org.aesh.command.invocation.CommandInvocation;
import org.aesh.readline.prompt.Prompt;

public class DetailView {

    public static void show(int port, CommandInvocation inv) throws Exception {
        var entries = Collector.collectAll(true);
        var entry = entries.stream().filter(e -> e.port() == port).findFirst()
                .orElseThrow(() -> new RuntimeException("No process found listening on port " + port));

        var proc = entry.process();

        inv.println("");
        inv.println("╔═══════════════════════════════════════════════════════════════╗");
        inv.println(String.format("║ %s Port %s%s║", proc.status().symbol(),
                Ansi.cyan(":" + port), pad(52 - String.valueOf(port).length())));
        inv.println("╠═══════════════════════════════════════════════════════════════╣");

        field(inv, "Process:", proc.name());
        field(inv, "PID:", String.valueOf(proc.pid()));
        if (proc.projectName() != null) field(inv, "Project:", proc.projectName());
        if (proc.cwd() != null) {
            String cwd = proc.cwd().length() > 45 ? "..." + proc.cwd().substring(proc.cwd().length() - 42) : proc.cwd();
            field(inv, "Path:", Ansi.dim(cwd));
        }
        if (proc.framework() != null)
            field(inv, "Framework:", proc.framework().emoji() + " " + proc.framework().displayName());
        if (proc.gitBranch() != null) field(inv, "Git Branch:", Ansi.green("🌿 " + proc.gitBranch()));
        field(inv, "Uptime:", proc.uptime());
        field(inv, "Memory:", "%.1f MB".formatted(proc.memoryMb()));
        field(inv, "Parent PID:", String.valueOf(proc.ppid()));

        inv.println("╠═══════════════════════════════════════════════════════════════╣");
        inv.println("║ Command:                                                      ║");
        for (String line : wrap(proc.command(), 59)) {
            inv.println(String.format("║ %-61s ║", Ansi.dim(line)));
        }
        inv.println("╚═══════════════════════════════════════════════════════════════╝");
        inv.println("");

        String prompt = String.format("%s %s [y/N]: ",
                Ansi.yellow("Kill this process?"), Ansi.dim("(PID " + proc.pid() + ")"));
        String raw = inv.getShell().readLine(new Prompt(prompt));
        String input = raw != null ? raw.trim().toLowerCase() : "";
        if ("y".equals(input)) {
            Platform.killGraceful(proc.pid());
        } else {
            Ansi.println(inv, Ansi.dim("Cancelled."));
        }
    }

    private static void field(CommandInvocation inv, String label, String value) {
        inv.println(String.format("║ %-15s %-45s ║", label, value));
    }

    private static String pad(int n) {
        return " ".repeat(Math.max(0, n));
    }

    private static java.util.List<String> wrap(String text, int width) {
        var lines = new java.util.ArrayList<String>();
        var current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() + word.length() + 1 > width && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
}
