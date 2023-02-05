package io.github.ya_b.registry.client.name;

import io.github.ya_b.registry.client.constant.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceTest {

    @Test
    void parse() {
        Reference reference = Reference.parse("openjdk:17-jdk");
        Assertions.assertEquals(Constants.ENDPOINT_DEFAULT, reference.getEndpoint());
        assertEquals("library/openjdk", reference.getName());
        assertEquals("17-jdk", reference.getTag());
        Assertions.assertNull(reference.getDigest());
    }

    @Test
    void parse1() {
        Reference reference = Reference.parse("localhost:5000/test:v1");
        assertEquals("localhost:5000", reference.getEndpoint());
        assertEquals("test", reference.getName());
        assertEquals("v1", reference.getTag());
        Assertions.assertNull(reference.getDigest());
    }

    @Test
    void parse2() {
        Reference reference = Reference.parse("localhost:5000/test:v1@sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2");
        assertEquals("localhost:5000", reference.getEndpoint());
        assertEquals("test", reference.getName());
        assertEquals("v1", reference.getTag());
        assertEquals("sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2", reference.getDigest());
    }

    @Test
    void parse3() {
        Reference reference = Reference.parse("localhost:5000/test");
        assertEquals("localhost:5000", reference.getEndpoint());
        assertEquals("test", reference.getName());
        assertEquals("latest", reference.getTag());
        Assertions.assertNull(reference.getDigest());
    }

    @Test
    void parse4() {
        Reference reference = Reference.parse("localhost:5000/test@sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2");
        assertEquals("localhost:5000", reference.getEndpoint());
        assertEquals("test", reference.getName());
        assertEquals("latest", reference.getTag());
        assertEquals("sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2", reference.getDigest());
    }

    @Test
    void parse5() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Reference.parse("localhost:5000/test:v1@b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Reference.parse("localhost:5000/test:v1@sha256"));
    }
}