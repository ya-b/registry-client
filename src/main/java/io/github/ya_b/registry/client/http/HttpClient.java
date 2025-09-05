package io.github.ya_b.registry.client.http;

import com.google.cloud.tools.jib.http.FailoverHttpClient;

public class HttpClient {

    public static FailoverHttpClient get() {
        FailoverHttpClient client = new FailoverHttpClient(true, true, ignored -> {});
        return client;
    }

}
