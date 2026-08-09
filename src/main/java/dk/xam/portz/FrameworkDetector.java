package dk.xam.portz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class FrameworkDetector {

    public static Framework detect(String cwd, String cmdline) {
        if (cwd != null) {
            Framework fw = detectFromPackageJson(Path.of(cwd));
            if (fw != null) return fw;
            fw = detectFromJavaProject(Path.of(cwd));
            if (fw != null) return fw;
        }
        Framework fw = detectFromCmdline(cmdline);
        if (fw != null) return fw;
        return detectFromProcessName(cmdline);
    }

    private static Framework detectFromPackageJson(Path dir) {
        Path packageJson = dir.resolve("package.json");
        if (!Files.exists(packageJson)) return null;
        try {
            String content = Files.readString(packageJson);
            // ponytail: simple contains checks instead of full JSON parse — works for dep detection
            if (content.contains("\"next\"")) return Framework.NEXTJS;
            if (content.contains("\"vite\"")) return Framework.VITE;
            if (content.contains("\"@angular/core\"")) return Framework.ANGULAR;
            if (content.contains("\"@remix-run")) return Framework.REMIX;
            if (content.contains("\"astro\"")) return Framework.ASTRO;
            if (content.contains("\"express\"")) return Framework.EXPRESS;
            if (content.contains("\"fastify\"")) return Framework.FASTIFY;
            if (content.contains("\"nuxt\"")) return Framework.NUXT;
        } catch (Exception _) {}
        return null;
    }

    private static Framework detectFromJavaProject(Path dir) {
        // Check pom.xml for Spring Boot / Quarkus / Micronaut
        Path pom = dir.resolve("pom.xml");
        if (Files.exists(pom)) {
            try {
                String content = Files.readString(pom);
                if (content.contains("spring-boot")) return Framework.SPRING_BOOT;
                if (content.contains("quarkus")) return Framework.QUARKUS;
                if (content.contains("micronaut")) return Framework.MICRONAUT;
            } catch (Exception _) {}
        }
        // Check build.gradle
        Path gradle = dir.resolve("build.gradle");
        if (!Files.exists(gradle)) gradle = dir.resolve("build.gradle.kts");
        if (Files.exists(gradle)) {
            try {
                String content = Files.readString(gradle);
                if (content.contains("spring-boot")) return Framework.SPRING_BOOT;
                if (content.contains("quarkus")) return Framework.QUARKUS;
                if (content.contains("micronaut")) return Framework.MICRONAUT;
            } catch (Exception _) {}
        }
        return null;
    }

    private static Framework detectFromCmdline(String cmdline) {
        if (cmdline == null) return null;
        String lower = cmdline.toLowerCase();
        if (lower.contains("django")) return Framework.DJANGO;
        if (lower.contains("uvicorn") || lower.contains("fastapi")) return Framework.FASTAPI;
        if (lower.contains("flask")) return Framework.FLASK;
        if (lower.contains("rails")) return Framework.RAILS;
        if (lower.contains("puma")) return Framework.PUMA;
        if (lower.contains("cargo run") || lower.contains("cargo watch")) return Framework.CARGO;
        if (lower.contains("go run")) return Framework.GO;
        if (lower.contains("spring-boot") || lower.contains("spring.boot")) return Framework.SPRING_BOOT;
        if (lower.contains("quarkus")) return Framework.QUARKUS;
        if (lower.contains("micronaut")) return Framework.MICRONAUT;
        return null;
    }

    private static Framework detectFromProcessName(String cmdline) {
        if (cmdline == null) return null;
        String lower = cmdline.toLowerCase();
        if (lower.startsWith("node")) return Framework.unknown("Node.js");
        if (lower.startsWith("python")) return Framework.unknown("Python");
        if (lower.startsWith("ruby")) return Framework.unknown("Ruby");
        if (lower.startsWith("go")) return Framework.GO;
        if (lower.startsWith("cargo")) return Framework.CARGO;
        return null;
    }
}
