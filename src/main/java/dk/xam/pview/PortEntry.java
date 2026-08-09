package dk.xam.pview;

public record PortEntry(int port, long pid, String address, ProcessInfo process) {
}
