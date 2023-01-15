package io.github.ya_b.registry.client.http.token;

public class DockerHubToken extends Token {

    public DockerHubToken(String endpoint, String username, String password) {
        super(endpoint, username, password);
    }

    @Override
    public String token() {

        return null;
    }
}
