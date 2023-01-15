package io.github.ya_b.registry.client.file;

import io.github.ya_b.registry.client.blob.Blob;
import io.github.ya_b.registry.client.blob.FileBlob;
import io.github.ya_b.registry.client.constant.Constants;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompressUtils {

    public static List<Blob> extractTar(InputStream is, Path dst) throws IOException {
        List<Blob> blobs = new ArrayList<>();
        try (TarArchiveInputStream tarArchiveIs = new TarArchiveInputStream(is)) {
            TarArchiveEntry entry = null;
            while ((entry = tarArchiveIs.getNextTarEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (!tarArchiveIs.canReadEntryData(entry)) throw new IOException("read tar entry error");
                Path itemPath = dst.resolve(FileUtil.replacePathChar(entry.getName()));
                FileUtils.forceMkdirParent(itemPath.toFile());
                Sha256HashOutputStream sha256HashOutputStream;
                try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(itemPath))) {
                    sha256HashOutputStream = new Sha256HashOutputStream(os);
                    IOUtils.copyRange(tarArchiveIs, entry.getSize(), sha256HashOutputStream);
                }
                blobs.add(new FileBlob(itemPath.toUri(), entry.getName(), entry.getSize(), Constants.SHA256_PREFIX + sha256HashOutputStream.hash()));
            }
        }
        return blobs;
    }


    public static Blob gzCompress(Path path) throws IOException {
        Path temp = path.resolveSibling(UUID.randomUUID().toString());
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path))) {
            return gzCompress(bis, temp);
        }
    }

    public static Blob gzCompress(InputStream is, Path temp) throws IOException {
        byte[] buffer = new byte[4096];
        Path compressed;
        Sha256HashOutputStream sha256HashOutputStream;
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(temp))) {
            sha256HashOutputStream = new Sha256HashOutputStream(os);
            GzipCompressorOutputStream gzOS = new GzipCompressorOutputStream(sha256HashOutputStream);
            IOUtils.copy(is, gzOS);
            gzOS.finish();
        }
        String sha256 = sha256HashOutputStream.hash();
        return new FileBlob(temp.toUri(), temp.toFile().getName(), Files.size(temp), Constants.SHA256_PREFIX + sha256);
    }
}
