package dk.xam.pview;

import java.util.ArrayList;
import java.util.List;

public class Renderer {

    public static void renderPortsTable(List<PortEntry> entries, boolean showAll) {
        if (entries.isEmpty()) {
            System.out.println(Ansi.yellow("No listening ports found."));
            return;
        }

        String[] headers = {"PORT", "PROCESS", "PID", "PROJECT", "FRAMEWORK", "UPTIME", "STATUS"};
        var rows = new ArrayList<String[]>();
        for (var e : entries) {
            String fw = e.process().framework() != null
                    ? e.process().framework().emoji() + " " + e.process().framework().displayName()
                    : "-";
            rows.add(new String[]{
                    Ansi.cyan(":" + e.port()),
                    e.process().name(),
                    String.valueOf(e.pid()),
                    e.process().projectName() != null ? e.process().projectName() : "-",
                    fw,
                    e.process().uptime(),
                    e.process().status().symbol()
            });
        }

        printTable(headers, rows);

        // Footer
        String filter = showAll ? "" : " · " + Ansi.dim("--all to show everything");
        System.out.printf("%n%s %s active%s%n",
                Ansi.cyan(String.valueOf(entries.size())),
                entries.size() == 1 ? "port" : "ports",
                filter);
        System.out.println("Run " + Ansi.dim("ports <number>") + " for details");
    }

    public static void renderOrphanTable(List<PortEntry> orphans) {
        String[] headers = {"PID", "PROCESS", "PROJECT", "UPTIME", "STATUS"};
        var rows = new ArrayList<String[]>();
        for (var e : orphans) {
            rows.add(new String[]{
                    String.valueOf(e.pid()),
                    e.process().name(),
                    e.process().projectName() != null ? e.process().projectName() : "-",
                    e.process().uptime(),
                    e.process().status().symbol()
            });
        }
        printTable(headers, rows);
    }

    // ponytail: simple table renderer, ~30 lines beats pulling a dependency
    private static void printTable(String[] headers, List<String[]> rows) {
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) widths[i] = stripAnsi(headers[i]).length();
        for (var row : rows) {
            for (int i = 0; i < cols; i++) {
                widths[i] = Math.max(widths[i], stripAnsi(row[i]).length());
            }
        }

        System.out.println(border('╭', '┬', '╮', widths));
        System.out.println(formatRow(headers, widths));
        System.out.println(border('├', '┼', '┤', widths));
        for (var row : rows) {
            System.out.println(formatRow(row, widths));
        }
        System.out.println(border('╰', '┴', '╯', widths));
    }

    private static String formatRow(String[] cells, int[] widths) {
        var sb = new StringBuilder("│");
        for (int i = 0; i < cells.length; i++) {
            int pad = widths[i] - stripAnsi(cells[i]).length();
            sb.append(' ').append(cells[i]).append(" ".repeat(pad + 1)).append('│');
        }
        return sb.toString();
    }

    private static String border(char left, char mid, char right, int[] widths) {
        var sb = new StringBuilder().append(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            sb.append(i < widths.length - 1 ? mid : right);
        }
        return sb.toString();
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\033\\[[0-9;]*m", "");
    }
}
