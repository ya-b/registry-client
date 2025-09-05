package io.github.ya_b.registry.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
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

class DockerHubClientTest {
    private static String username;

    @BeforeAll
    static void auth() throws IOException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("ROOT");
        logger.setLevel(Level.DEBUG);
        username = System.getenv("DOCKER_USERNAME");
        RegistryClient.authDockerHub(username, System.getenv("DOCKER_PASSWORD"));
    }

    @Test
    void digest() throws Exception {
        Optional<String> digest = RegistryClient.digest("registry:latest");
        Assertions.assertTrue(digest.get().startsWith("sha256:"));
    }

    @Test
    void tags() throws Exception {
        List<String> tags = RegistryClient.tags("registry");
        Assertions.assertTrue(tags.contains("latest"));
    }

    @Test
    void dockerIOPullPush() throws IOException {
        Path path = Files.createTempFile(UUID.randomUUID().toString(), ".tar");
        RegistryClient.pull("registry:latest", path.toString());
        Assertions.assertTrue(Files.exists(path));
        RegistryClient.push(path.toString(), "%s/registry:2".formatted(username));
        Assertions.assertTrue(RegistryClient.digest("%s/registry:2".formatted(username)).isPresent());
        Files.delete(path);
        RegistryClient.delete("%s/registry:2".formatted(username));
    }

    @Test
    void dockerIOCopy() throws IOException {
        RegistryClient.copy("registry:latest",
                "%s/registry:0905".formatted(username));
        Assertions.assertTrue(RegistryClient.digest("%s/registry:0905".formatted(username)).isPresent());
        RegistryClient.delete("%s/registry:0905".formatted(username));
    }

}