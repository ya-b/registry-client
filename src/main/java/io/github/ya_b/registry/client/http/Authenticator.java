package io.github.ya_b.registry.client.http;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.http.Request;
import com.google.cloud.tools.jib.http.Response;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;

import lombok.Data;

public class Authenticator {
    public static final String DOCKER_HUB_REGISTRY;

    @Data
    private static class TokenTemplate implements JsonTemplate {
        private String token;
        private String access_token;
        private int expires_in;
        private String issued_at;
    }

    static {
        try {
            // Parse a valid Docker Hub image to get the registry value
            DOCKER_HUB_REGISTRY = ImageReference.parse("library").getRegistry();
        } catch (InvalidImageReferenceException e) {
            throw new RuntimeException("Failed to initialize Docker Hub registry constant", e);
        }
    }

    public static Optional<Authorization> getAuthorization(String endpoint, String[] credentials, String repository,
            Scope scope) throws IOException {
        if (credentials == null || credentials.length != 2) {
            return Optional.empty();
        }
        Authorization authorization = Authorization.fromBasicCredentials(credentials[0], credentials[1]);
        if (!endpoint.contains(DOCKER_HUB_REGISTRY)) {
            return Optional.of(authorization);
        }

        FailoverHttpClient httpClient = HttpClient.get();
        URL url = URI
                .create(String.format("https://auth.docker.io/token?service=registry.docker.io&scope=repository:%s:%s",
                        repository, scope.getScope()))
                .toURL();
        Response response = httpClient.get(url,
                Request.builder().setHttpTimeout(3000).setAuthorization(authorization).build());
        TokenTemplate tokenResponse = JsonTemplateMapper.readJson(response.getBody(), TokenTemplate.class);
        return Optional.of(Authorization.fromBearerToken(tokenResponse.token));
    }

}
