package io.github.ya_b.registry.client;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import io.github.ya_b.registry.client.http.resp.CatalogResp;
import io.github.ya_b.registry.client.jib.JibImageManager;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RegistryClient {

    private static final JibImageManager JIB_IMAGE_MANAGER = new JibImageManager();

    // Simple credential storage for Jib
    private static final Map<String, String[]> CREDENTIALS = new ConcurrentHashMap<>();
    private static String[] dockerHubCredentials = null;

    static {
        
        System.setProperty("sendCredentialsOverHttp", "true");
        System.setProperty("jib.httpTimeout", "30000");
        System.setProperty("jib.allowInsecureRegistries", "true");
    }

    /**
     * Set basic authentication for a registry endpoint
     */
    public static void authBasic(String endpoint, String username, String password) {
        CREDENTIALS.put(normalizeEndpoint(endpoint), new String[]{username, password});
    }

    /**
     * Set Docker Hub authentication
     */
    public static void authDockerHub(String username, String password) {
        dockerHubCredentials = new String[]{username, password};
    }

    /**
     * Extract registry endpoint from image reference string
     */
    private static String extractEndpoint(String imageReference) throws InvalidImageReferenceException {
        ImageReference imageRef = ImageReference.parse(imageReference);
        String registry = imageRef.getRegistry();

        // Add protocol prefix for endpoint matching
        if (registry.startsWith("localhost") || registry.startsWith("127.0.0.1") || registry.startsWith("0.0.0.0")) {
            return "http://" + registry;
        } else {
            return "https://" + registry;
        }
    }

    /**
     * Get credentials for an endpoint
     */
    static String[] getCredentials(String endpoint) {
        if (endpoint.contains("docker.io") || endpoint.contains("index.docker.io")) {
            return dockerHubCredentials;
        }
        return CREDENTIALS.get(normalizeEndpoint(endpoint));
    }

    private static String normalizeEndpoint(String endpoint) {
        return endpoint.replaceAll("^https?://", "").replaceAll("/$", "");
    }

    public static void push(String filePath, String image) throws IOException {
        try {
            // Set credentials for Jib
            String endpoint = extractEndpoint(image);
            String[] credentials = getCredentials(endpoint);


            JIB_IMAGE_MANAGER.pushToRegistry(filePath, image, credentials);
        } catch (InvalidImageReferenceException e) {
            log.error("Invalid image reference while pushing", e);
            throw new IOException("Invalid image reference", e);
        }
    }


    public static void pull(String image, String filePath) throws IOException {
        try {
            // Set credentials for Jib
            String endpoint = extractEndpoint(image);
            String[] credentials = getCredentials(endpoint);

            JIB_IMAGE_MANAGER.saveToTar(ImageReference.parse(image), credentials, filePath);
        } catch (InvalidImageReferenceException e) {
            log.error("Invalid image reference while pulling", e);
            throw new IOException("Invalid image reference", e);
        }
    }

    public static Optional<String> digest(String image) throws IOException {
        try {
            // Set credentials for Jib
            String endpoint = extractEndpoint(image);
            String[] credentials = getCredentials(endpoint);

            return JIB_IMAGE_MANAGER.getDigest(image, credentials);
        } catch (InvalidImageReferenceException e) {
            log.error("Invalid image reference while getting digest", e);
            throw new IOException("Invalid image reference", e);
        }
    }

    public static List<String> tags(String image) throws IOException {
        try {
            // Set credentials for Jib
            String endpoint = extractEndpoint(image);
            String[] credentials = getCredentials(endpoint);

            return JIB_IMAGE_MANAGER.getTags(image, credentials);
        } catch (InvalidImageReferenceException e) {
            log.error("Invalid image reference while getting tags", e);
            throw new IOException("Invalid image reference", e);
        }
    }

    public static void delete(String image) throws IOException {
        try {
            // Set credentials for registry API
            String endpoint = extractEndpoint(image);
            String[] credentials = getCredentials(endpoint);

            JIB_IMAGE_MANAGER.deleteImage(image, credentials);
        } catch (InvalidImageReferenceException e) {
            log.error("Invalid image reference while deleting", e);
            throw new IOException("Invalid image reference", e);
        }
    }

    public static void copy(String src, String dst) throws IOException {
        try {
            // Set credentials for Jib
            String srcEndpoint = extractEndpoint(src);
            String dstEndpoint = extractEndpoint(dst);
            String[] srcCredentials = getCredentials(srcEndpoint);
            String[] dstCredentials = getCredentials(dstEndpoint);

            JIB_IMAGE_MANAGER.pushToRegistry(src, srcCredentials, dst, dstCredentials);
        } catch (InvalidImageReferenceException e) {
            log.error("Invalid image reference while copying", e);
            throw new IOException("Invalid image reference", e);
        }
    }

    public static CatalogResp catalog(String url, Integer count, String last) throws IOException {
        try {
            // Normalize the registry URL and get credentials
            String normalizedUrl = normalizeRegistryUrl(url);
            String[] credentials = getCredentials(normalizedUrl);

            return JIB_IMAGE_MANAGER.getCatalog(normalizedUrl, credentials, count, last);
        } catch (Exception e) {
            log.error("Failed to get catalog from registry: {}", url, e);
            throw new IOException("Failed to get catalog", e);
        }
    }

    /**
     * Normalize registry URL for consistent credential lookup
     */
    private static String normalizeRegistryUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Registry URL cannot be null or empty");
        }

        String normalized = url.trim();

        // Add protocol if missing
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            // Check if it's a local registry
            if (normalized.startsWith("localhost") || normalized.startsWith("127.0.0.1") || normalized.startsWith("0.0.0.0")) {
                normalized = "http://" + normalized;
            } else {
                normalized = "https://" + normalized;
            }
        }

        return normalized;
    }
}
