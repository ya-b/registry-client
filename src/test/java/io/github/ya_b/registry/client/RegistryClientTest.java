package io.github.ya_b.registry.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.github.ya_b.registry.client.http.resp.CatalogResp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class RegistryClientTest {

    @BeforeAll
    static void auth() throws IOException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("ROOT");
        logger.setLevel(Level.DEBUG);
        RegistryClient.authBasic("localhost:5000", "admin", "123456");
        RegistryClient.authDockerHub(System.getenv("DOCKER_USERNAME"), System.getenv("DOCKER_PASSWORD"));
    }

    @Test
    void digest() throws Exception {
        Optional<String> digest = RegistryClient.digest("localhost:5000/registry:latest");
        Assertions.assertTrue(digest.get().startsWith("sha256:"));
    }

    @Test
    void tags() throws Exception {
        List<String> tags = RegistryClient.tags("localhost:5000/registry");
        Assertions.assertTrue(tags.contains("latest"));
    }

    @Test
    void dockerIOPullPush() throws IOException {
        Path path = Files.createTempFile(UUID.randomUUID().toString(), ".tar");
        RegistryClient.pull("localhost:5000/registry:latest", path.toString());
        Assertions.assertTrue(Files.exists(path));
        RegistryClient.push(path.toString(), "localhost:5000/registry:2");
        Assertions.assertTrue(RegistryClient.digest("localhost:5000/registry:2").isPresent());
        Files.delete(path);
        RegistryClient.delete("localhost:5000/registry:2");
    }

    @Test
    void dockerIOCopy() throws IOException {
        RegistryClient.copy("localhost:5000/registry:latest",
                "localhost:5000/registry/test:latest");
        Assertions.assertTrue(RegistryClient.digest("localhost:5000/registry/test:latest").isPresent());
        RegistryClient.delete("localhost:5000/registry/test:latest");
    }

    @Test
    void registryCatalog() {
        Assertions.assertDoesNotThrow(() -> {
            CatalogResp catalogResp = RegistryClient.catalog("http://localhost:5000", 10, "test");
            Assertions.assertNotNull(catalogResp);
        });
    }
}