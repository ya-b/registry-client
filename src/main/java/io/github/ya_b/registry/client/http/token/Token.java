package io.github.ya_b.registry.client.http.token;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public abstract class Token {

    private String endpoint;

    private String username;

    private String password;

    public abstract String token();
}
