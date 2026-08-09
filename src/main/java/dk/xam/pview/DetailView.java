package dk.xam.pview;

import java.util.Scanner;

public class DetailView {

    public static void show(int port) throws Exception {
        var entries = Collector.collectAll(true);
        var entry = entries.stream().filter(e -> e.port() == port).findFirst()
                .orElseThrow(() -> new RuntimeException("No process found listening on port " + port));

        var proc = entry.process();

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.printf("║ %s Port %s%s║%n", proc.status().symbol(),
                Ansi.cyan(":" + port), pad(52 - String.valueOf(port).length()));
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");

        field("Process:", proc.name());
        field("PID:", String.valueOf(proc.pid()));
        if (proc.projectName() != null) field("Project:", proc.projectName());
        if (proc.cwd() != null) {
            String cwd = proc.cwd().length() > 45 ? "..." + proc.cwd().substring(proc.cwd().length() - 42) : proc.cwd();
            field("Path:", Ansi.dim(cwd));
        }
        if (proc.framework() != null)
            field("Framework:", proc.framework().emoji() + " " + proc.framework().displayName());
        if (proc.gitBranch() != null) field("Git Branch:", Ansi.green("🌿 " + proc.gitBranch()));
        field("Uptime:", proc.uptime());
        field("Memory:", "%.1f MB".formatted(proc.memoryMb()));
        field("Parent PID:", String.valueOf(proc.ppid()));

        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║ Command:                                                      ║");
        for (String line : wrap(proc.command(), 59)) {
            System.out.printf("║ %-61s ║%n", Ansi.dim(line));
        }
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.printf("%s %s [y/N]: ", Ansi.yellow("Kill this process?"), Ansi.dim("(PID " + proc.pid() + ")"));
        System.out.flush();
        var scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim().toLowerCase();
        if ("y".equals(input)) {
            Platform.killGraceful(proc.pid());
        } else {
            System.out.println(Ansi.dim("Cancelled."));
        }
    }

    private static void field(String label, String value) {
        // Visible widths inside the box
        System.out.printf("║ %-15s %-45s ║%n", label, value);
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
