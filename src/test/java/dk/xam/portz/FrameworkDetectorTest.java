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
}
