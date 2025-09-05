package io.github.ya_b.registry.client.jib;

import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;

import io.github.ya_b.registry.client.http.Authenticator;
import io.github.ya_b.registry.client.http.HttpClient;
import io.github.ya_b.registry.client.http.Scope;
import io.github.ya_b.registry.client.http.resp.CatalogResp;
import io.github.ya_b.registry.client.http.resp.TagsResp;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Pure Jib-based image manager that handles all container image operations
 * using Google's Jib library exclusively.
 */
@Slf4j
public class JibImageManager {


    /**
     * Save a Jib container builder as a tar file using Jib
     */
    public void saveToTar(ImageReference imageRef, String[] credentials, String filePath) throws IOException, InvalidImageReferenceException {
        Path targetTarFile = Path.of(filePath);
        try {
            RegistryImage registryImage = RegistryImage.named(imageRef);

            // Set authentication if credentials are provided
            if (credentials != null && credentials.length == 2) {
                registryImage.addCredential(credentials[0], credentials[1]);
            }

            TarImage targetImage = TarImage.at(targetTarFile).named(imageRef);

            // Configure containerizer with proper settings for HTTP registries
            Containerizer containerizer = Containerizer.to(targetImage)
                .setAllowInsecureRegistries(true)
                .setOfflineMode(false);

            // For localhost HTTP registries, we need special handling
            String registry = imageRef.getRegistry();
            if (isLocalHttpRegistry(registry)) {
                log.info("Detected local HTTP registry: {}, applying special configuration", registry);
                // For HTTP registries, we need to ensure credentials are sent
                if (credentials != null && credentials.length == 2) {
                    // Create a new registry image with explicit credential handling
                    registryImage = RegistryImage.named(imageRef);
                    registryImage.addCredential(credentials[0], credentials[1]);

                    // Additional configuration for HTTP
                    containerizer = containerizer
                        .setAllowInsecureRegistries(true)
                        .setOfflineMode(false);
                }
            }

            Jib.from(registryImage).containerize(containerizer);

            log.info("Successfully saved container to tar using Jib: {}", filePath);

        } catch (Exception e) {
            log.error("Failed to save container to tar using Jib", e);
            // Clean up the target file only if there was an error
            try {
                Files.deleteIfExists(targetTarFile);
            } catch (IOException cleanupException) {
                log.warn("Failed to clean up target file after error: {}", targetTarFile, cleanupException);
            }
            throw new IOException("Failed to save container to tar", e);
        }
    }

    /**
     * Check if the registry is a local HTTP registry
     */
    private boolean isLocalHttpRegistry(String registry) {
        return registry.startsWith("localhost") ||
               registry.startsWith("127.0.0.1") ||
               registry.startsWith("0.0.0.0");
    }

    /**
     * Push an image to registry using Jib
     */
    public void pushToRegistry(String srcReference, String[] srcCredentials, String destReference, String[] destCredentials) throws IOException, InvalidImageReferenceException {
        try {
            // Create registry image reference
            ImageReference dstRef = ImageReference.parse(destReference);
            RegistryImage dstRegistryImage = RegistryImage.named(dstRef);

            // Set authentication if credentials are provided
            if (destCredentials != null && destCredentials.length == 2) {
                dstRegistryImage.addCredential(destCredentials[0], destCredentials[1]);
            }


            // Create registry image reference
            ImageReference srcRef = ImageReference.parse(srcReference);
            RegistryImage srcRegistryImage = RegistryImage.named(srcRef);

            // Set authentication if credentials are provided
            if (srcCredentials != null && srcCredentials.length == 2) {
                srcRegistryImage.addCredential(srcCredentials[0], srcCredentials[1]);
            }

            JibContainer result = Jib.from(srcRegistryImage).containerize(Containerizer.to(dstRegistryImage).setAllowInsecureRegistries(true));

            log.info("Successfully pushed image to registry: {} with digest: {}",
                    destReference, result.getDigest());

        } catch (Exception e) {
            log.error("Failed to push image to registry using Jib", e);
            throw new IOException("Failed to push image to registry", e);
        }
    }

    
    public void pushToRegistry(String path, String destReference, String[] destCredentials) throws IOException, InvalidImageReferenceException {
        try {
            // Create registry image reference
            ImageReference dstRef = ImageReference.parse(destReference);
            RegistryImage dstRegistryImage = RegistryImage.named(dstRef);

            // Set authentication if credentials are provided
            if (destCredentials != null && destCredentials.length == 2) {
                dstRegistryImage.addCredential(destCredentials[0], destCredentials[1]);
            }
            TarImage tarImage = TarImage.at(Path.of(path)).named(destReference);
            JibContainer result = Jib.from(tarImage).containerize(Containerizer.to(dstRegistryImage).setAllowInsecureRegistries(true));

            log.info("Successfully pushed image to registry: {} with digest: {}",
                    destReference, result.getDigest());

        } catch (Exception e) {
            log.error("Failed to push image to registry using Jib", e);
            throw new IOException("Failed to push image to registry", e);
        }
    }

    /**
     * Get image digest from registry using HTTP client
     */
    public Optional<String> getDigest(String imageReference, String[] credentials) throws IOException, InvalidImageReferenceException {
        ImageReference imageRef = ImageReference.parse(imageReference);
        String registry = imageRef.getRegistry();
        String repository = imageRef.getRepository();
        String tag = imageRef.getTag().orElse("latest");

        // Build the registry API URL for getting manifest
        String protocol = (registry.startsWith("localhost") || registry.startsWith("127.0.0.1") || registry.startsWith("0.0.0.0"))
            ? "http" : "https";
        String apiUrl = String.format("%s://%s/v2/%s/manifests/%s", protocol, registry, repository, tag);

        log.info("Getting digest for image: {} from URL: {}", imageReference, apiUrl);

        FailoverHttpClient client = HttpClient.get();
        Request.Builder requestBuilder = Request.builder().setHttpTimeout(3000)
            .setAccept(Collections.singletonList("application/vnd.docker.distribution.manifest.v2+json"));

        Optional<Authorization> authOptional = Authenticator.getAuthorization(apiUrl, credentials, repository, Scope.PULL);
        if (authOptional.isPresent()) {
            requestBuilder.setAuthorization(authOptional.get());
        }
        Request request = requestBuilder.build();
        Response response = client.call("HEAD", URI.create(apiUrl).toURL(), request);

        if (response.getStatusCode() == 200) {
            // Get digest from Docker-Content-Digest header
            
            List<String> digestHeader = response.getHeader("Docker-Content-Digest");

            if (digestHeader.size() > 0) {
                String digest = digestHeader.get(0);
                log.info("Successfully retrieved digest for image: {} -> {}", imageReference, digest);
                return Optional.of(digest);
            } else {
                log.warn("No Docker-Content-Digest header found for image: {}", imageReference);
                return Optional.empty();
            }
        } else {
            log.error("Failed to get digest for image: {}. HTTP status: {}",
                imageReference, response.getStatusCode());
            throw new IOException("Failed to get digest. HTTP status: " + response.getStatusCode());
        }
    }

    /**
     * Get image tags from registry using HTTP client
     */
    public List<String> getTags(String imageReference, String[] credentials) throws IOException, InvalidImageReferenceException {
        ImageReference imageRef = ImageReference.parse(imageReference);
        String registry = imageRef.getRegistry();
        String repository = imageRef.getRepository();

        // Build the registry API URL for listing tags
        String protocol = (registry.startsWith("localhost") || registry.startsWith("127.0.0.1") || registry.startsWith("0.0.0.0"))
            ? "http" : "https";
        String apiUrl = String.format("%s://%s/v2/%s/tags/list", protocol, registry, repository);

        log.info("Getting tags for image: {} from URL: {}", imageReference, apiUrl);

        FailoverHttpClient client = HttpClient.get();
        Request.Builder requestBuilder = Request.builder().setHttpTimeout(3000);

        Optional<Authorization> authOptional = Authenticator.getAuthorization(apiUrl, credentials, repository, Scope.PULL);
        if (authOptional.isPresent()) {
            requestBuilder.setAuthorization(authOptional.get());
        }
        Request request = requestBuilder.build();
        Response response = client.get(URI.create(apiUrl).toURL(), request);


        if (response.getStatusCode() == 200) {
            TagsResp tagsResp = JsonTemplateMapper.readJson(response.getBody(), TagsResp.class);

            if (tagsResp != null && tagsResp.getTags() != null) {
                log.info("Successfully retrieved {} tags for image: {}", tagsResp.getTags().size(), imageReference);
                return tagsResp.getTags();
            } else {
                log.warn("No tags found in response for image: {}", imageReference);
                return Collections.emptyList();
            }
        } else {
            log.error("Failed to get tags for image: {}. HTTP status: {}, response: {}",
                imageReference, response.getStatusCode(), new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            throw new IOException("Failed to get tags. HTTP status: " + response.getStatusCode());
        }
    }

    /**
     * Delete image from registry using HTTP client
     * This method first gets the digest of the image, then deletes it using the digest
     */
    public void deleteImage(String imageReference, String[] credentials) throws IOException, InvalidImageReferenceException {

        ImageReference imageRef = ImageReference.parse(imageReference);
        String registry = imageRef.getRegistry();
        String repository = imageRef.getRepository();

        log.info("Deleting image: {}", imageReference);

        // Step 1: Get the digest of the image
        Optional<String> digestOpt = getDigest(imageReference, credentials);
        if (!digestOpt.isPresent()) {
            throw new IOException("Cannot delete image: unable to get digest for " + imageReference);
        }
        String digest = digestOpt.get();

        // Step 2: Delete the image using the digest
        String protocol = (registry.startsWith("localhost") || registry.startsWith("127.0.0.1") || registry.startsWith("0.0.0.0"))
            ? "http" : "https";
        String deleteUrl = String.format("%s://%s/v2/%s/manifests/%s", protocol, registry, repository, digest);

        log.info("Deleting image manifest from URL: {}", deleteUrl);
        FailoverHttpClient client = HttpClient.get();
        Request.Builder requestBuilder = Request.builder().setHttpTimeout(3000);

        Optional<Authorization> authOptional = Authenticator.getAuthorization(deleteUrl, credentials, repository, Scope.DELETE);
        if (authOptional.isPresent()) {
            requestBuilder.setAuthorization(authOptional.get());
        }
        Request request = requestBuilder.build();
        Response response = client.call("DELETE", URI.create(deleteUrl).toURL(), request);
        if (response.getStatusCode() == 202 || response.getStatusCode() == 204) {
            log.info("Successfully deleted image: {} (digest: {})", imageReference, digest);
        } else if (response.getStatusCode() == 404) {
            log.warn("Image not found for deletion: {} (digest: {})", imageReference, digest);
            throw new IOException("Image not found: " + imageReference);
        } else if (response.getStatusCode() == 405) {
            log.error("Delete operation not supported by registry for image: {}", imageReference);
            throw new IOException("Delete operation not supported by registry");
        } else {
            log.error("Failed to delete image: {}. HTTP status: {}, response: {}",
                imageReference, response.getStatusCode(), new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            throw new IOException("Failed to delete image. HTTP status: " + response.getStatusCode());
        }
    }

    /**
     * Get catalog (list of repositories) from registry using HTTP client
     */
    public CatalogResp getCatalog(String registryUrl, String[] credentials, Integer count, String last) throws IOException {

        // Normalize registry URL
        String normalizedUrl = registryUrl.replaceAll("/$", "");

        // Build the registry API URL for catalog
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(normalizedUrl).append("/v2/_catalog");

        // Add query parameters if provided
        boolean hasParams = false;
        if (count != null && count > 0) {
            urlBuilder.append("?n=").append(count);
            hasParams = true;
        }
        if (last != null && !last.trim().isEmpty()) {
            urlBuilder.append(hasParams ? "&" : "?").append("last=").append(last.trim());
        }

        String apiUrl = urlBuilder.toString();
        log.info("Getting catalog from registry URL: {}", apiUrl);
        
        FailoverHttpClient client = HttpClient.get();
        Request.Builder requestBuilder = Request.builder().setHttpTimeout(3000);

        Optional<Authorization> authOptional = Authenticator.getAuthorization(apiUrl, credentials, null, Scope.PULL);
        if (authOptional.isPresent()) {
            requestBuilder.setAuthorization(authOptional.get());
        }
        Request request = requestBuilder.build();
        Response response = client.get(URI.create(apiUrl).toURL(), request);

        if (response.getStatusCode() == 200) {
            CatalogResp catalogResp = JsonTemplateMapper.readJson(response.getBody(), CatalogResp.class);

            if (catalogResp != null) {
                // Check for pagination link in Link header
                List<String> linkHeader = response.getHeader("Link");
                if (linkHeader.size() > 0) {
                    // Parse Link header to extract next page info
                    String linkValue = linkHeader.get(0);
                    // Link header format: </v2/_catalog?last=repo&n=100>; rel="next"
                    if (linkValue.contains("rel=\"next\"")) {
                        // Extract the last parameter from the link
                        int lastIndex = linkValue.indexOf("last=");
                        if (lastIndex != -1) {
                            int endIndex = linkValue.indexOf("&", lastIndex);
                            if (endIndex == -1) {
                                endIndex = linkValue.indexOf(">", lastIndex);
                            }
                            if (endIndex != -1) {
                                String nextLast = linkValue.substring(lastIndex + 5, endIndex);
                                catalogResp.setNext(nextLast);
                            }
                        }
                    }
                }

                int repoCount = catalogResp.getRepositories() != null ? catalogResp.getRepositories().size() : 0;
                log.info("Successfully retrieved catalog with {} repositories from registry", repoCount);
                return catalogResp;
            } else {
                log.warn("Empty catalog response from registry");
                return new CatalogResp();
            }
        } else {
            log.error("Failed to get catalog from registry. HTTP status: {}, response: {}",
                response.getStatusCode(), new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            throw new IOException("Failed to get catalog. HTTP status: " + response.getStatusCode());
        }
    }

}
