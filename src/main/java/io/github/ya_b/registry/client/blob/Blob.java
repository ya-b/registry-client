package io.github.ya_b.registry.client.blob;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class Blob {

    // file or url
    private URI path;
    // manifest.json中的名字。tar entry.name
    private String name;
    private Long size;
    private String digest;

    public void copyFrom(Blob blob) {
        this.path = blob.getPath();
        this.name = blob.getName();
        this.size = blob.getSize();
        this.digest = blob.getDigest();
    }

    public abstract InputStream inputStream() throws IOException;
}
