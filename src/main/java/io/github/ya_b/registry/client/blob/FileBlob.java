package io.github.ya_b.registry.client.blob;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileBlob extends Blob {

    public FileBlob(URI path, String name, Long size, String digest) {
        super(path, name, size, digest);
    }

    @Override
    public InputStream inputStream() throws IOException {
        return Files.newInputStream(Paths.get(getPath()));
    }
}
