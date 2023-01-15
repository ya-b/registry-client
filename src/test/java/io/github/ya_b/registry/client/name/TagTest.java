package io.github.ya_b.registry.client.name;

import io.github.ya_b.registry.client.constant.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TagTest {

    @Test
    void parse() {
        Tag tag = Tag.parse("openjdk:17-jdk");
        Assertions.assertEquals(Constants.ENDPOINT_DEFAULT, tag.getEndpoint());
        assertEquals("library/openjdk", tag.getName());
        assertEquals("17-jdk", tag.getTag());
        Assertions.assertNull(tag.getDigest());
    }

    @Test
    void parse1() {
        Tag tag = Tag.parse("localhost:5000/test:v1");
        assertEquals("localhost:5000", tag.getEndpoint());
        assertEquals("test", tag.getName());
        assertEquals("v1", tag.getTag());
        Assertions.assertNull(tag.getDigest());
    }

    @Test
    void parse2() {
        Tag tag = Tag.parse("localhost:5000/test:v1@sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2");
        assertEquals("localhost:5000", tag.getEndpoint());
        assertEquals("test", tag.getName());
        assertEquals("v1", tag.getTag());
        assertEquals("sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2", tag.getDigest());
    }

    @Test
    void parse3() {
        Tag tag = Tag.parse("localhost:5000/test");
        assertEquals("localhost:5000", tag.getEndpoint());
        assertEquals("test", tag.getName());
        assertEquals("latest", tag.getTag());
        Assertions.assertNull(tag.getDigest());
    }

    @Test
    void parse4() {
        Tag tag = Tag.parse("localhost:5000/test@sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2");
        assertEquals("localhost:5000", tag.getEndpoint());
        assertEquals("test", tag.getName());
        assertEquals("latest", tag.getTag());
        assertEquals("sha256:b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2", tag.getDigest());
    }

    @Test
    void parse5() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Tag.parse("localhost:5000/test:v1@b8604a3fe8543c9e6afc29550de05b36cd162a97aa9b2833864ea8a5be11f3e2"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Tag.parse("localhost:5000/test:v1@sha256"));
    }
}