package dk.xam.portz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FrameworkDetectorTest {

    @Test void detectDjangoFromCmdline() {
        assertEquals(Framework.DJANGO, FrameworkDetector.detectFromCmdline("python manage.py runserver django"));
    }

    @Test void detectFastAPIFromCmdline() {
        assertEquals(Framework.FASTAPI, FrameworkDetector.detectFromCmdline("uvicorn main:app"));
    }

    @Test void detectFlaskFromCmdline() {
        assertEquals(Framework.FLASK, FrameworkDetector.detectFromCmdline("flask run"));
    }

    @Test void detectRailsFromCmdline() {
        assertEquals(Framework.RAILS, FrameworkDetector.detectFromCmdline("rails server"));
    }

    @Test void detectQuarkusFromCmdline() {
        assertEquals(Framework.QUARKUS, FrameworkDetector.detectFromCmdline("java -jar quarkus-run.jar"));
    }

    @Test void detectSpringBootFromCmdline() {
        assertEquals(Framework.SPRING_BOOT, FrameworkDetector.detectFromCmdline("java -jar app.jar --spring.boot.admin"));
    }

    @Test void noMatchReturnsNull() {
        assertNull(FrameworkDetector.detectFromCmdline("some-random-binary --flag"));
    }

    @Test void detectNextJsFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "next": "14.0.0", "react": "18.0.0" } }
                """);
        assertEquals(Framework.NEXTJS, FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void detectExpressFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "express": "4.18.0" } }
                """);
        assertEquals(Framework.EXPRESS, FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void noPackageJsonReturnsNull(@TempDir Path dir) {
        assertNull(FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void detectQuarkusFromPom(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
                <project><dependencies>
                  <dependency><artifactId>quarkus-core</artifactId></dependency>
                </dependencies></project>
                """);
        assertEquals(Framework.QUARKUS, FrameworkDetector.detectFromJavaProject(dir));
    }

    @Test void detectSpringBootFromGradle(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("build.gradle"), """
                plugins { id 'org.springframework.boot' }
                dependencies { implementation 'org.springframework.boot:spring-boot-starter' }
                """);
        assertEquals(Framework.SPRING_BOOT, FrameworkDetector.detectFromJavaProject(dir));
    }

    @Test void detectMicronautFromPom(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
                <project><dependencies>
                  <dependency><artifactId>micronaut-http-server</artifactId></dependency>
                </dependencies></project>
                """);
        assertEquals(Framework.MICRONAUT, FrameworkDetector.detectFromJavaProject(dir));
    }

    @Test void detectMicronautFromGradleKts(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("build.gradle.kts"), """
                dependencies { implementation("io.micronaut:micronaut-http-server") }
                """);
        assertEquals(Framework.MICRONAUT, FrameworkDetector.detectFromJavaProject(dir));
    }

    // === Full detect() pipeline tests ===

    @Test void detectPrioritizesPackageJsonOverCmdline(@TempDir Path dir) throws Exception {
        // package.json says Next.js, cmdline says django — package.json wins
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "next": "14.0.0" } }
                """);
        var d = FrameworkDetector.detect(dir.toString(), "python manage.py django");
        assertEquals(Framework.NEXTJS, d.framework());
        assertEquals(Runtime.PYTHON, d.runtime());
    }

    @Test void detectFallsThroughToJavaProject(@TempDir Path dir) throws Exception {
        // No package.json, but pom.xml has quarkus
        Files.writeString(dir.resolve("pom.xml"), """
                <project><dependencies>
                  <dependency><artifactId>quarkus-core</artifactId></dependency>
                </dependencies></project>
                """);
        var d = FrameworkDetector.detect(dir.toString(), "java -jar app.jar");
        assertEquals(Framework.QUARKUS, d.framework());
        assertEquals(Runtime.JAVA, d.runtime());
    }

    @Test void detectFallsThroughToCmdline(@TempDir Path dir) {
        // No package.json, no pom.xml — detect from cmdline
        var d = FrameworkDetector.detect(dir.toString(), "python manage.py runserver django");
        assertEquals(Framework.DJANGO, d.framework());
        assertEquals(Runtime.PYTHON, d.runtime());
    }

    @Test void detectNullCwdUsesCmdline() {
        var d = FrameworkDetector.detect(null, "flask run");
        assertEquals(Framework.FLASK, d.framework());
    }

    @Test void detectNothingReturnsNull(@TempDir Path dir) {
        var d = FrameworkDetector.detect(dir.toString(), "some-unknown-binary");
        assertNull(d.framework());
        assertNull(d.runtime());
    }

    @Test void detectRuntimeOnly(@TempDir Path dir) {
        var d = FrameworkDetector.detect(dir.toString(), "node server.js");
        assertNull(d.framework());
        assertEquals(Runtime.NODEJS, d.runtime());
    }

    @Test void detectRuntimeAndFramework(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "express": "4.18.0" } }
                """);
        var d = FrameworkDetector.detect(dir.toString(), "node server.js");
        assertEquals(Framework.EXPRESS, d.framework());
        assertEquals(Runtime.NODEJS, d.runtime());
    }

    @Test void detectViteFromDevDeps(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "devDependencies": { "vite": "5.0.0" } }
                """);
        assertEquals(Framework.VITE, FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void detectAngularFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "@angular/core": "17.0.0" } }
                """);
        assertEquals(Framework.ANGULAR, FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void detectFastifyFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "fastify": "4.0.0" } }
                """);
        assertEquals(Framework.FASTIFY, FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void detectNuxtFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "nuxt": "3.0.0" } }
                """);
        assertEquals(Framework.NUXT, FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void detectRemixFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "@remix-run/node": "2.0.0" } }
                """);
        assertEquals(Framework.REMIX, FrameworkDetector.detectFromPackageJson(dir));
    }

    @Test void detectAstroFromPackageJson(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), """
                { "dependencies": { "astro": "4.0.0" } }
                """);
        assertEquals(Framework.ASTRO, FrameworkDetector.detectFromPackageJson(dir));
    }
}
