package dk.xam.pview;

import java.util.*;
import java.util.regex.*;

public class DockerInfo {

    record Container(String id, String name, String image, List<PortMapping> ports) {
        String detectService() {
            String lower = image.toLowerCase();
            if (lower.contains("postgres")) return "PostgreSQL";
            if (lower.contains("redis")) return "Redis";
            if (lower.contains("mongo")) return "MongoDB";
            if (lower.contains("mysql") || lower.contains("mariadb")) return "MySQL";
            if (lower.contains("nginx")) return "Nginx";
            if (lower.contains("rabbitmq")) return "RabbitMQ";
            if (lower.contains("elasticsearch")) return "Elasticsearch";
            if (lower.contains("kafka")) return "Kafka";
            return null;
        }
    }

    record PortMapping(int hostPort, int containerPort) {}

    private static final Pattern PORT_RE = Pattern.compile("0\\.0\\.0\\.0:(\\d+)->(\\d+)/tcp");

    static List<Container> collectContainers() {
        try {
            var pb = new ProcessBuilder("docker", "ps", "--format", "json");
            pb.redirectErrorStream(false);
            var proc = pb.start();
            String stdout = new String(proc.getInputStream().readAllBytes());
            if (proc.waitFor() != 0) return List.of();

            var containers = new ArrayList<Container>();
            for (String line : stdout.lines().toList()) {
                if (line.isBlank()) continue;
                // ponytail: manual JSON field extraction — avoids Jackson dependency for one use
                String id = jsonField(line, "ID");
                String names = jsonField(line, "Names");
                String image = jsonField(line, "Image");
                String ports = jsonField(line, "Ports");
                containers.add(new Container(id, names, image, parsePorts(ports)));
            }
            return containers;
        } catch (Exception e) {
            return List.of();
        }
    }

    static Container findForPort(List<Container> containers, int port) {
        return containers.stream()
                .filter(c -> c.ports.stream().anyMatch(p -> p.hostPort == port))
                .findFirst().orElse(null);
    }

    private static List<PortMapping> parsePorts(String portsStr) {
        if (portsStr == null) return List.of();
        var mappings = new ArrayList<PortMapping>();
        var matcher = PORT_RE.matcher(portsStr);
        while (matcher.find()) {
            try {
                mappings.add(new PortMapping(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
            } catch (NumberFormatException ignored) {}
        }
        return mappings;
    }

    private static String jsonField(String json, String field) {
        // ponytail: regex json extraction, good enough for docker ps output
        var m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
