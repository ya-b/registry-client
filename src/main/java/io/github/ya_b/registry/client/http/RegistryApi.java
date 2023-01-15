package io.github.ya_b.registry.client.http;

import io.github.ya_b.registry.client.constant.Constants;
import io.github.ya_b.registry.client.entity.remote.ManifestHttp;
import io.github.ya_b.registry.client.exception.HttpCodeErrorException;
import io.github.ya_b.registry.client.exception.RegistryException;
import io.github.ya_b.registry.client.json.JsonUtil;
import io.github.ya_b.registry.client.image.ImageMediaType;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RegistryApi {

    private static final String BASE = "%s://%s/v2/";

    private static final String CATALOG = "%s://%s/v2/_catalog";

    private static final String TAGS = "%s://%s/v2/%s/tags/list";

    private static final String MANIFEST = "%s://%s/v2/%s/manifests/%s";

    private static final String BLOB = "%s://%s/v2/%s/blobs/%s";

    private static final String BLOB_UPLOAD = "%s://%s/v2/%s/blobs/uploads/";

    private Map<String, String> schemaMap = new ConcurrentHashMap<>();

    private String getSchema(String endpoint) {
        if (schemaMap.containsKey(endpoint)) {
            return schemaMap.get(endpoint);
        }
        try (Response response = HttpClient.getInstance().head(String.format(BASE, Constants.SCHEMA_HTTPS, endpoint))) {
            schemaMap.put(endpoint, Constants.SCHEMA_HTTPS);
            return Constants.SCHEMA_HTTPS;
        } catch (Throwable e) {
            log.debug("registry not support https");
        }
        schemaMap.put(endpoint, Constants.SCHEMA_HTTP);
        return Constants.SCHEMA_HTTP;
    }

    public String manifestUrl(String endpoint, String name, String tag) {
        return String.format(MANIFEST, getSchema(endpoint), endpoint, name, tag);
    }

    public String blobUrl(String endpoint, String name, String layerDigest) {
        return String.format(BLOB, getSchema(endpoint), endpoint, name, layerDigest);
    }

    public String blobUploadUrl(String endpoint, String name) {
        return String.format(BLOB_UPLOAD, getSchema(endpoint), endpoint, name);
    }

    public Optional<String> digest(String endpoint, String name, String tag) throws IOException {
        Headers headers = Headers.of(HttpHeaders.ACCEPT, "application/vnd.docker.distribution.manifest.v1+json," +
                "application/vnd.docker.distribution.manifest.v1+prettyjws," +
                "application/vnd.docker.distribution.manifest.v2+json," +
                "application/vnd.oci.image.manifest.v1+json," +
                "application/vnd.docker.distribution.manifest.list.v2+json," +
                "application/vnd.oci.image.index.v1+json");
        try (Response response = HttpClient.getInstance().head(manifestUrl(endpoint, name, tag), headers)) {
            if (response.isSuccessful()) {
                return Optional.ofNullable(response.header("Docker-Content-Digest"));
            }
            if (response.code() == 404) {
                return Optional.empty();
            }
            throw responseException(response);
        }
    }

    public Optional<ManifestHttp> getManifest(String endpoint, String name, String tag) throws IOException {
        Headers headers = Headers.of(HttpHeaders.ACCEPT, ImageMediaType.MANIFEST_V2.toString());
        try (Response response = HttpClient.getInstance().get(manifestUrl(endpoint, name, tag), headers)) {
            if (!response.isSuccessful()) {
                throw responseException(response);
            }
            if (response.body() != null) {
                return Optional.ofNullable(JsonUtil.fromJson(response.body().string(), ManifestHttp.class));
            }
        }
        return Optional.empty();
    }

    public InputStream getBlob(String endpoint, String name, String layerDigest) throws IOException {
        return getBlob(blobUrl(endpoint, name, layerDigest));
    }

    public InputStream getBlob(String url) throws IOException {
        Response response = HttpClient.getInstance().get(url);
        if (response.isSuccessful()) {
            if (response.body() != null) {
                return response.body().byteStream();
            }
        }
        throw responseException(response);
    }

    public boolean isBlobExists(String endpoint, String name, String layerDigest) throws IOException {
        try (Response response = HttpClient.getInstance().head(blobUrl(endpoint, name, layerDigest))) {
            return response.isSuccessful();
        }
    }

    public String startPush(String endpoint, String name) throws IOException {
        String url = blobUploadUrl(endpoint, name);
        try (Response response = HttpClient.getInstance().post(url,
                RequestBody.create("", MediaType.parse(ImageMediaType.LAYER.toString())))) {
            if (!response.isSuccessful()) {
                throw responseException(response);
            }
            return HttpClient.getInstance().getLocation(response, url);
        }
    }

    public Optional<String> mountBlob(String endpoint, String dstName, String digest, String srcName) throws IOException {
        String mountUrl = blobUploadUrl(endpoint, dstName) + String.format("?mount=%s&from=%s", digest, srcName);
        try (Response response = HttpClient.getInstance().post(mountUrl,
                RequestBody.create(new byte[0], MediaType.parse(ImageMediaType.MANIFEST_V2.toString())))) {
            if (response.code() == 201) {
                return Optional.empty();
            }
            if (response.code() == 202) {
                return Optional.of(HttpClient.getInstance().getLocation(response, endpoint));
            }
            throw responseException(response);
        }
    }

    public void uploadBlob(String url, String digest, InputStream inputStream, Long length) throws IOException {
        String appendQuery = new URL(url).getQuery() == null ? "?" : "&";
        appendQuery += "digest=" + digest;
        RequestBody body = new InputStreamRequestBody(inputStream, length, MediaType.parse("application/octet-stream"));
        try (Response response = HttpClient.getInstance().put(url + appendQuery, body)) {
            if (!response.isSuccessful()) {
                throw responseException(response);
            }
        }
    }

    public void deleteLayer(String endpoint, String name, String digest) throws IOException {
        try (Response response = HttpClient.getInstance().delete(blobUrl(endpoint, name, digest))) {
            if (!response.isSuccessful()) {
                throw responseException(response);
            }
        }
    }

    public void deleteManifest(String endpoint, String name, String digest) throws IOException {
        try (Response response = HttpClient.getInstance().delete(manifestUrl(endpoint, name, digest))) {
            if (!response.isSuccessful()) {
                throw responseException(response);
            }
        }
    }

    public void uploadManifest(String endpoint, String name, String tag, ManifestHttp content, ImageMediaType contentType) throws IOException {
        RequestBody body = RequestBody.create(JsonUtil.toJson(content), MediaType.parse(contentType.toString()));
        try (Response response = HttpClient.getInstance().put(manifestUrl(endpoint, name, tag), body)) {
            if (!response.isSuccessful()) {
                throw responseException(response);
            }
        }
    }

    private IOException responseException(Response response) throws IOException {
        if (response.body() == null) {
            throw new HttpCodeErrorException("status code:" + response.code());
        }
        ErrorResponse error = JsonUtil.fromJson(response.body().string(), ErrorResponse.class);
        if (error == null || error.getErrors() == null || error.getErrors().isEmpty()) {
            throw new HttpCodeErrorException("status code:" + response.code());
        }
        throw new RegistryException(error.toString());
    }
}
