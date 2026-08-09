package dk.xam.portz;

public record PortEntry(int port, long pid, String address, ProcessInfo process) {
}
