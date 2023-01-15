package io.github.ya_b.registry.client.file;

import io.github.ya_b.registry.client.constant.FileConstant;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public class FileUtil {

    public static URI gzWithSha256Filename(URI uri) throws IOException {
        Path path = Paths.get(uri);
        Path temp = path.resolveSibling(UUID.randomUUID().toString());
        byte[] buffer = new byte[4096];
        Path compressed;
        try (InputStream is = Files.newInputStream(path);
             Sha256HashOutputStream os = new Sha256HashOutputStream(Files.newOutputStream(temp));
             GZIPOutputStream gzOS = new GZIPOutputStream(os)) {
            IOUtils.copy(is, gzOS);
            compressed = temp.resolveSibling(os.hash() + FileConstant.EXTENSION_TAR_GZ);
        }
        boolean isSucceeded = temp.toFile().renameTo(compressed.toFile());
        if (!isSucceeded) {
            throw new IOException("compress layer error");
        }
        return compressed.toUri();
    }


    public static String replacePathChar(String str) {
        return str.replaceAll("[:*?\"<>|]", "/");
    }

    protected static MessageDigest sha256Digest() {
        MessageDigest sha256Digest = null;
        try {
            sha256Digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return sha256Digest;
    }

    public static String sha256(Path path) throws IOException {
        MessageDigest sha256Digest = sha256Digest();
        byte[] buffer = new byte[4096];
        try (InputStream is = Files.newInputStream(path)) {
            int len;
            while ((len = is.read(buffer)) != -1) {
                sha256Digest.update(buffer, 0, len);
            }
        }
        BigInteger number = new BigInteger(1, sha256Digest.digest());
        StringBuilder hexString = new StringBuilder(number.toString(16));
        while (hexString.length() < 64) {
            hexString.insert(0, '0');
        }
        return hexString.toString();
    }
}
