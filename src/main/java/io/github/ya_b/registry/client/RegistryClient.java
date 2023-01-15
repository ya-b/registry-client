package io.github.ya_b.registry.client;

import io.github.ya_b.registry.client.constant.Constants;
import io.github.ya_b.registry.client.http.HttpClient;
import io.github.ya_b.registry.client.http.token.BasicToken;
import io.github.ya_b.registry.client.http.token.DockerHubToken;
import io.github.ya_b.registry.client.image.FileOperate;
import io.github.ya_b.registry.client.image.Image;
import io.github.ya_b.registry.client.image.RegistryOperate;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
public class RegistryClient {

    private static final FileOperate FILE_OPERATE = new FileOperate();

    private static final RegistryOperate REGISTRY_OPERATE = new RegistryOperate();

    public static void authBasic(String endpoint, String username, String password) {
        HttpClient.getInstance().auth(new BasicToken(endpoint, username, password));
    }

    public static void authDockerHub(String username, String password) {
        HttpClient.getInstance().auth(new DockerHubToken(Constants.ENDPOINT_DEFAULT, username, password));
    }

    public static void push(String filePath, String image) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(Paths.get(filePath)))) {
            push(is, image);
        }
    }

    public static void push(InputStream is, String image) throws IOException {
        Image img = FILE_OPERATE.load(is);
        REGISTRY_OPERATE.push(img, image);
    }

    public static void pull(String image, String filePath) throws IOException {
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            pull(image, os);
        }
    }

    public static void pull(String image, OutputStream outputStream) throws IOException {
        Image img = REGISTRY_OPERATE.load(image);
        FILE_OPERATE.save(img, outputStream);
    }

    public static Optional<String> digest(String image) throws IOException {
        return REGISTRY_OPERATE.digest(image);
    }

    public static void delete(String image) throws IOException {
        REGISTRY_OPERATE.delete(image);
    }

    public static void copy(String src, String dst) throws IOException {
        REGISTRY_OPERATE.copy(src, dst);
    }
}
