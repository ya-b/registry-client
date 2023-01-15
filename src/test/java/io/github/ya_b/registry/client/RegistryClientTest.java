package io.github.ya_b.registry.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

class RegistryClientTest {

    @BeforeAll
    static void auth() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("ROOT");
        logger.setLevel(Level.DEBUG);
        RegistryClient.authBasic("localhost:5000", "admin", "123456");
    }

    @Test
    void push() throws IOException {
        Optional<String> digest = RegistryClient.digest("localhost:5000/test:v1");
        if (digest.isPresent()) {
            RegistryClient.delete("localhost:5000/test:v1@" + digest.get());
        }
        digest = RegistryClient.digest("localhost:5000/test:v1");
        Assertions.assertFalse(digest.isPresent());
        RegistryClient.push("C:\\tmp\\docker.tar", "localhost:5000/test:v1");
        digest = RegistryClient.digest("localhost:5000/test:v1");
        System.out.println(digest.orElse(null));
        Assertions.assertTrue(digest.isPresent());
    }

    @Test
    void pushDockerSave() throws IOException {
        Optional<String> digest = RegistryClient.digest("localhost:5000/test:v2");
        if (digest.isPresent()) {
            RegistryClient.delete("localhost:5000/test:v1@" + digest.get());
        }
        digest = RegistryClient.digest("localhost:5000/test:v2");
        Assertions.assertFalse(digest.isPresent());
        RegistryClient.push("C:\\tmp\\save.tar", "localhost:5000/test:v2");
        digest = RegistryClient.digest("localhost:5000/test:v2");
        System.out.println(digest.orElse(null));
        Assertions.assertTrue(digest.isPresent());
    }

    @Test
    void pushOci() throws IOException {
        Optional<String> digest = RegistryClient.digest("localhost:5000/test:v3");
        if (digest.isPresent()) {
            RegistryClient.delete("localhost:5000/test:v1@" + digest.get());
        }
        digest = RegistryClient.digest("localhost:5000/test:v3");
        Assertions.assertFalse(digest.isPresent());
        RegistryClient.push("C:\\tmp\\oci.tar", "localhost:5000/test:v3");
        digest = RegistryClient.digest("localhost:5000/test:v3");
        System.out.println(digest.orElse(null));
        Assertions.assertTrue(digest.isPresent());
    }

    @Test
    void pull() throws IOException {
        FileUtils.deleteQuietly(new File("C:\\tmp\\docker2.tar"));
        RegistryClient.pull("localhost:5000/test:v3", "C:\\tmp\\docker2.tar");
        Assertions.assertTrue(Files.exists(Paths.get("C:\\tmp\\docker2.tar")));
    }

    @Test
    void copy() throws IOException {
        Optional<String> digest = RegistryClient.digest("localhost:5000/test2:v1");
        if (digest.isPresent()) {
            RegistryClient.delete("localhost:5000/test2:v1@" + digest.get());
        }
        RegistryClient.copy("localhost:5000/test:v1", "localhost:5000/test2:v1");
        digest = RegistryClient.digest("localhost:5000/test2:v1");
        System.out.println(digest.orElse(null));
        Assertions.assertTrue(digest.isPresent());
    }
}