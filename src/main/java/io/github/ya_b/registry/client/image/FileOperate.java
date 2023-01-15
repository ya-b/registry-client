package io.github.ya_b.registry.client.image;

import com.google.gson.reflect.TypeToken;
import io.github.ya_b.registry.client.blob.Blob;
import io.github.ya_b.registry.client.constant.Constants;
import io.github.ya_b.registry.client.constant.FileConstant;
import io.github.ya_b.registry.client.entity.file.IndexFile;
import io.github.ya_b.registry.client.entity.file.ManifestFile;
import io.github.ya_b.registry.client.entity.remote.ManifestHttp;
import io.github.ya_b.registry.client.exception.FormatNotSupportException;
import io.github.ya_b.registry.client.exception.TarFileErrException;
import io.github.ya_b.registry.client.file.CompressUtils;
import io.github.ya_b.registry.client.file.FileUtil;
import io.github.ya_b.registry.client.json.JsonUtil;
import io.github.ya_b.registry.client.name.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class FileOperate {


    public Image load(InputStream is) throws IOException {
        Path dst = Paths.get(FileUtils.getTempDirectoryPath(), UUID.randomUUID().toString());
        List<Blob> extractFiles = CompressUtils.extractTar(is, dst);
        Format format = imageType(extractFiles);
        if (Format.DOCKER.equals(format)) {
            extractFiles = gzTarItem(extractFiles);
            return readManifest(extractFiles, dst);
        } else if (Format.OCI.equals(format)) {
            return readIndex(extractFiles, dst);
        }
        throw new FormatNotSupportException("format not support");
    }

    public void save(Image image, OutputStream os) throws IOException {
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(os)) {
            TarArchiveEntry manifestEntry = new TarArchiveEntry(FileConstant.MANIFEST);
            byte[] manifestContent = JsonUtil.toJson(Collections.singletonList(manifest(image))).getBytes(StandardCharsets.UTF_8);
            manifestEntry.setSize(manifestContent.length);
            tos.putArchiveEntry(manifestEntry);
            tos.write(manifestContent);
            tos.closeArchiveEntry();
            List<Blob> blobs = new ArrayList<>(image.getLayers());
            blobs.add(image.getConfig());
            for (Blob blob : blobs) {
                TarArchiveEntry layerEntry = new TarArchiveEntry(blob.getName());
                layerEntry.setSize(blob.getSize());
                tos.putArchiveEntry(layerEntry);
                try (InputStream is = blob.inputStream()) {
                    IOUtils.copy(is, tos);
                }
                tos.closeArchiveEntry();
            }
        }
    }

    private List<Blob> gzTarItem(List<Blob> layers) throws IOException {
        List<Blob> result = new ArrayList<>();
        for (Blob blob : layers) {
            if (new File(blob.getPath()).getName().endsWith(FileConstant.EXTENSION_TAR)) {
                Blob compressedBlob = CompressUtils.gzCompress(Paths.get(blob.getPath()));
                compressedBlob.setName(blob.getName());
                result.add(compressedBlob);
            } else {
                result.add(blob);
            }
        }
        return result;
    }


    private Image readIndex(List<Blob> files, Path dir) throws IOException {
        Function<String, Blob> findBlob = name -> files.stream()
                .filter(p -> Objects.equals(Paths.get(p.getName()), Paths.get(name))).findFirst().orElse(null);
        Blob index = findBlob.apply(FileConstant.INDEX);
        assert index != null;
        String indexContent = FileUtils.readFileToString(new File(index.getPath()), StandardCharsets.UTF_8);
        IndexFile indexFile = JsonUtil.fromJson(indexContent, IndexFile.class);
        assert indexFile.getManifests().size() > 0;
        Path manifestPath = Paths.get(Constants.PATH_BLOBS, FileUtil.replacePathChar(indexFile.getManifests().get(0).getDigest()));
        String manifestContent = FileUtils.readFileToString(dir.resolve(manifestPath).toFile(), StandardCharsets.UTF_8);
        ManifestHttp manifestHttp = JsonUtil.fromJson(manifestContent, ManifestHttp.class);
        Blob config = findBlob.apply(Paths.get(Constants.PATH_BLOBS, FileUtil.replacePathChar(manifestHttp.getConfig().getDigest())).toString());
        List<Blob> layers = manifestHttp.getLayers().stream()
                .map(layer -> Paths.get(Constants.PATH_BLOBS, FileUtil.replacePathChar(layer.getDigest())).toString())
                .map(findBlob)
                .collect(Collectors.toList());
        if (config == null || layers.stream().anyMatch(Objects::isNull)) {
            throw new TarFileErrException("file missing");
        }
        config.setName(config.getDigest().replace(Constants.SHA256_PREFIX, "") + FileConstant.EXTENSION_TAR_GZ);
        layers.forEach(layer ->
                layer.setName(layer.getDigest().replace(Constants.SHA256_PREFIX, "") + FileConstant.EXTENSION_TAR_GZ));
        return new Image(Tag.parse(indexFile.getManifests().get(0).getAnnotations().getImageRefName()), config, layers);
    }

    private Image readManifest(List<Blob> files, Path dir) throws IOException {
        Function<String, Blob> findBlob = name -> files.stream()
                .filter(p -> Objects.equals(Paths.get(p.getName()), Paths.get(name))).findFirst().orElse(null);
        Blob manifest = findBlob.apply(FileConstant.MANIFEST);
        assert manifest != null;
        String manifestContent = FileUtils.readFileToString(new File(manifest.getPath()), StandardCharsets.UTF_8);
        List<ManifestFile> manifestFiles = JsonUtil.fromJson(manifestContent, new TypeToken<List<ManifestFile>>() {});
        if (manifestFiles.size() == 0) throw new TarFileErrException("manifest.json error");
        ManifestFile manifestFile = manifestFiles.get(0);
        Blob config = findBlob.apply(manifestFile.getConfig());
        List<Blob> layers = manifestFiles.get(0).getLayers().stream().map(findBlob).collect(Collectors.toList());
        if (config == null || layers.stream().anyMatch(Objects::isNull)) {
            throw new TarFileErrException("file missing");
        }
        config.setName(config.getDigest().replace(Constants.SHA256_PREFIX, "") + FileConstant.EXTENSION_TAR_GZ);
        layers.forEach(layer ->
                layer.setName(layer.getDigest().replace(Constants.SHA256_PREFIX, "") + FileConstant.EXTENSION_TAR_GZ));
        return new Image(Tag.parse(manifestFiles.get(0).getRepoTags().get(0)), config, layers);
    }

    private Format imageType(List<Blob> extractFiles) throws FormatNotSupportException {
        for (Blob extractFile : extractFiles) {
            String filename = new File(extractFile.getPath()).getName();
            if (FileConstant.MANIFEST.equals(filename)) {
                return Format.DOCKER;
            } else if (FileConstant.INDEX.equals(filename)) {
                return Format.OCI;
            }
        }
        throw new FormatNotSupportException("manifest not found in tar");
    }

    private ManifestFile manifest(Image image) {
        ManifestFile manifestFile = new ManifestFile();
        manifestFile.setConfig(image.getConfig().getName());
        manifestFile.setRepoTags(Collections.singletonList(image.getTag().toString()));
        manifestFile.setLayers(image.getLayers().stream().map(Blob::getName).collect(Collectors.toList()));
        return manifestFile;
    }
}
