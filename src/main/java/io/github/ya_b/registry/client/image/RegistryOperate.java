package io.github.ya_b.registry.client.image;

import io.github.ya_b.registry.client.blob.Blob;
import io.github.ya_b.registry.client.blob.RegistryBlob;
import io.github.ya_b.registry.client.constant.Constants;
import io.github.ya_b.registry.client.constant.FileConstant;
import io.github.ya_b.registry.client.entity.remote.ManifestHttp;
import io.github.ya_b.registry.client.exception.RegistryException;
import io.github.ya_b.registry.client.http.RegistryApi;
import io.github.ya_b.registry.client.name.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class RegistryOperate {

    private RegistryApi api = new RegistryApi();

    public Image load(String image) throws IOException {
        Tag tag = Tag.parse(image);
        Optional<ManifestHttp> manifestHttp = api.getManifest(tag.getEndpoint(), tag.getName(), tag.getTag());
        if (!manifestHttp.isPresent()) {
            throw new RegistryException("not support");
        }
        ManifestHttp.BlobDTO configInfo = manifestHttp.get().getConfig();
        String configUrl = api.blobUrl(tag.getEndpoint(), tag.getName(), configInfo.getDigest());
        String configName = configInfo.getDigest().replace(Constants.SHA256_PREFIX, "") + FileConstant.EXTENSION_JSON;
        Blob config = new RegistryBlob(URI.create(configUrl), configName, configInfo.getSize(), configInfo.getDigest());
        List<Blob> layers = new ArrayList<>();
        for (ManifestHttp.BlobDTO blob : manifestHttp.get().getLayers()) {
            String layerUrl = api.blobUrl(tag.getEndpoint(), tag.getName(), blob.getDigest());
            String layerName = blob.getDigest().replace(Constants.SHA256_PREFIX, "") + FileConstant.EXTENSION_TAR_GZ;
            layers.add(new RegistryBlob(URI.create(layerUrl), layerName, blob.getSize(), blob.getDigest()));
        }
        return new Image(tag, config, layers);
    }

    public void push(Image image, String name) throws IOException {
        Tag tag = Tag.parse(name);
        List<Blob> blobList = new ArrayList<>(image.getLayers());
        blobList.add(image.getConfig());
        for (Blob blob : blobList) {
            if (!api.isBlobExists(tag.getEndpoint(), tag.getName(), blob.getDigest())) {
                String uploadUrl = api.startPush(tag.getEndpoint(), tag.getName());
                try (InputStream is = blob.inputStream()) {
                    api.uploadBlob(uploadUrl, blob.getDigest(), is, blob.getSize());
                }
                if (!api.isBlobExists(tag.getEndpoint(), tag.getName(), blob.getDigest())) {
                    throw new RegistryException("upload blob failed");
                }
            }
        }
        ManifestHttp manifestHttp = manifest(image);
        api.uploadManifest(tag.getEndpoint(), tag.getName(), tag.getTag(), manifestHttp, manifestHttp.getMediaType());
    }

    public Optional<String> digest(String image) throws IOException {
        Tag tag = Tag.parse(image);
        return api.digest(tag.getEndpoint(), tag.getName(), tag.getTag());
    }

    public void delete(String image) throws IOException {
        Tag tag = Tag.parse(image);
        api.deleteManifest(tag.getEndpoint(), tag.getName(), tag.getDigest());
    }

    public void copy(String src, String dst) throws IOException {
        Tag srcTag = Tag.parse(src);
        Tag dstTag = Tag.parse(dst);
        Image image = load(src);
        if (!Objects.equals(srcTag.getEndpoint(), dstTag.getEndpoint())) {
            push(image, dst);
            return;
        }
        List<Blob> blobList = new ArrayList<>(image.getLayers());
        blobList.add(image.getConfig());
        for (Blob blob : blobList) {
            if (!api.isBlobExists(dstTag.getEndpoint(), dstTag.getName(), blob.getDigest())) {
                Optional<String> uploadUrl = api.mountBlob(dstTag.getEndpoint(), dstTag.getName(), blob.getDigest(), srcTag.getName());
                if (uploadUrl.isPresent()) {
                    try (InputStream is = blob.inputStream()) {
                        api.uploadBlob(uploadUrl.get(), blob.getDigest(), is, blob.getSize());
                    }
                }
                if (!api.isBlobExists(dstTag.getEndpoint(), dstTag.getName(), blob.getDigest())) {
                    throw new RegistryException("upload blob failed");
                }
            }
        }
        ManifestHttp manifestHttp = manifest(image);
        api.uploadManifest(dstTag.getEndpoint(), dstTag.getName(), dstTag.getTag(), manifestHttp, manifestHttp.getMediaType());
    }

    private ManifestHttp manifest(Image image) {
        ManifestHttp manifest = new ManifestHttp();
        manifest.setSchemaVersion(Constants.SCHEMA_V_2);
        manifest.setMediaType(ImageMediaType.MANIFEST_V2);
        manifest.setConfig(new ManifestHttp.BlobDTO(ImageMediaType.CONFIG, image.getConfig().getSize(), image.getConfig().getDigest()));
        manifest.setLayers(image.getLayers().stream().map(blob -> new ManifestHttp.BlobDTO(ImageMediaType.CONFIG, blob.getSize(),
                blob.getDigest())).collect(Collectors.toList()));
        return manifest;
    }
}
