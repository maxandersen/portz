package dk.xam.pview;

import picocli.CommandLine.Command;

import java.util.Scanner;

@Command(name = "clean", description = "Find and interactively kill orphaned processes")
public class CleanCommand implements Runnable {

    @Override
    public void run() {
        try {
            var entries = Collector.collectAll(true);
            var orphans = entries.stream()
                    .filter(e -> (e.process().status() == ProcessStatus.ORPHANED || e.process().status() == ProcessStatus.ZOMBIE)
                            && e.process().isDevProcess())
                    .toList();

            if (orphans.isEmpty()) {
                System.out.println(Ansi.green("✓ No orphaned processes found."));
                return;
            }

            System.out.printf("%s %s orphaned process%s:%n%n",
                    Ansi.yellow("Found"), Ansi.yellow(String.valueOf(orphans.size())),
                    orphans.size() == 1 ? "" : "es");

            Renderer.renderOrphanTable(orphans);
            System.out.println();

            var scanner = new Scanner(System.in);
            for (var entry : orphans) {
                System.out.printf("%s PID %s %s [y/N/a(ll)/q(uit)]: ",
                        Ansi.yellow("Kill"), Ansi.bold(String.valueOf(entry.pid())),
                        Ansi.dim(entry.process().name()));
                System.out.flush();

                String choice = scanner.nextLine().trim().toLowerCase();
                switch (choice) {
                    case "y" -> Platform.killGraceful(entry.pid());
                    case "a" -> {
                        System.out.println(Ansi.yellow("Killing all orphans..."));
                        orphans.forEach(e -> Platform.killGraceful(e.pid()));
                        System.out.println("\n" + Ansi.green("✓ Cleanup complete."));
                        return;
                    }
                    case "q" -> {
                        System.out.println(Ansi.dim("Cancelled."));
                        return;
                    }
                    default -> System.out.println(Ansi.dim("Skipped."));
                }
            }
            System.out.println("\n" + Ansi.green("✓ Cleanup complete."));
        } catch (Exception e) {
            System.err.println(Ansi.red("Error: " + e.getMessage()));
        }
    }
}
