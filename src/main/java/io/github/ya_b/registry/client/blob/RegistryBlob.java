package io.github.ya_b.registry.client.blob;

import io.github.ya_b.registry.client.exception.RegistryException;
import io.github.ya_b.registry.client.http.HttpClient;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class RegistryBlob extends Blob {

    public RegistryBlob(URI path, String name, Long size, String digest) {
        super(path, name, size, digest);
    }

    @Override
    public InputStream inputStream() throws IOException {
        Response response = HttpClient.getInstance().get(getPath().toString());
        if (!response.isSuccessful() || response.body() == null) {
            throw new RegistryException("read blob error");
        }
        return response.body().byteStream();
    }
}