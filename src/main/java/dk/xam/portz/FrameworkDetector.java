package dk.xam.portz;

import java.nio.file.Files;
import java.nio.file.Path;

/** Detects runtime and framework from CWD files and command line. */
public class FrameworkDetector {

    record Detection(Runtime runtime, Framework framework) {}

    public static Detection detect(String cwd, String cmdline) {
        Runtime runtime = detectRuntime(cmdline);
        Framework framework = null;

        if (cwd != null) {
            framework = detectFromPackageJson(Path.of(cwd));
            if (framework != null && runtime == null) runtime = Runtime.NODEJS;

            if (framework == null) {
                framework = detectFromJavaProject(Path.of(cwd));
                if (runtime == null && (framework != null || Files.exists(Path.of(cwd).resolve("pom.xml"))
                        || Files.exists(Path.of(cwd).resolve("build.gradle"))
                        || Files.exists(Path.of(cwd).resolve("build.gradle.kts")))) {
                    runtime = Runtime.JAVA;
                }
            }

            if (runtime == null) {
                if (Files.exists(Path.of(cwd).resolve("Cargo.toml"))) runtime = Runtime.RUST;
                else if (Files.exists(Path.of(cwd).resolve("go.mod"))) runtime = Runtime.GO;
                else if (Files.exists(Path.of(cwd).resolve("requirements.txt"))
                        || Files.exists(Path.of(cwd).resolve("pyproject.toml"))) runtime = Runtime.PYTHON;
                else if (Files.exists(Path.of(cwd).resolve("Gemfile"))) runtime = Runtime.RUBY;
                else if (Files.exists(Path.of(cwd).resolve("mix.exs"))) runtime = Runtime.ELIXIR;
                else if (Files.exists(Path.of(cwd).resolve("composer.json"))) runtime = Runtime.PHP;
            }
        }

        if (framework == null) framework = detectFromCmdline(cmdline);

        return new Detection(runtime, framework);
    }

    private static Runtime detectRuntime(String cmdline) {
        if (cmdline == null) return null;
        String first = cmdline.split("\\s+")[0].toLowerCase();
        int slash = first.lastIndexOf('/');
        String bin = slash >= 0 ? first.substring(slash + 1) : first;

        return switch (bin) {
            case "java", "javac" -> Runtime.JAVA;
            case "node", "nodejs" -> Runtime.NODEJS;
            case "python", "python3" -> Runtime.PYTHON;
            case "ruby" -> Runtime.RUBY;
            case "go" -> Runtime.GO;
            case "cargo" -> Runtime.RUST;
            case "php" -> Runtime.PHP;
            case "dotnet" -> Runtime.DOTNET;
            case "elixir", "mix" -> Runtime.ELIXIR;
            case "deno" -> Runtime.DENO;
            case "bun" -> Runtime.BUN;
            default -> {
                if (bin.startsWith("python")) yield Runtime.PYTHON;
                if (first.contains("/java/") || first.contains("/jdk/") || first.contains("/jre/")) yield Runtime.JAVA;
                if (first.contains("/node_modules/")) yield Runtime.NODEJS;
                yield null;
            }
        };
    }

    private static Framework detectFromPackageJson(Path dir) {
        Path packageJson = dir.resolve("package.json");
        if (!Files.exists(packageJson)) return null;
        try {
            String content = Files.readString(packageJson);
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
        Path pom = dir.resolve("pom.xml");
        if (Files.exists(pom)) {
            try {
                String content = Files.readString(pom);
                if (content.contains("spring-boot")) return Framework.SPRING_BOOT;
                if (content.contains("quarkus")) return Framework.QUARKUS;
                if (content.contains("micronaut")) return Framework.MICRONAUT;
            } catch (Exception _) {}
        }
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
        if (lower.contains("spring-boot") || lower.contains("spring.boot")) return Framework.SPRING_BOOT;
        if (lower.contains("quarkus")) return Framework.QUARKUS;
        if (lower.contains("micronaut")) return Framework.MICRONAUT;
        return null;
    }
}
