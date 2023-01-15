package io.github.ya_b.registry.client.http.token;

import okhttp3.Credentials;

public class BasicToken extends Token {

    public BasicToken(String endpoint, String username, String password) {
        super(endpoint, username, password);
    }

    @Override
    public String token() {
        return Credentials.basic(getUsername(), getPassword());
    }
}
