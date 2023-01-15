package io.github.ya_b.registry.client.http;

import io.github.ya_b.registry.client.constant.Constants;
import io.github.ya_b.registry.client.exception.RegistryException;
import io.github.ya_b.registry.client.http.token.DockerHubToken;
import io.github.ya_b.registry.client.http.token.Token;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class HttpClient {

    private static final String METHOD_GET = "GET";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_PATCH = "PATCH";

    private static final String URL_PREFIX = "(http|https)://";

    private static final String URL_SUFFIX = "/{1}.*";

    private static volatile HttpClient instance = null;

    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request request = chain.request();
                String requestId = UUID.randomUUID().toString();
                log.debug(String.format("requestId: %s, %s : %s", requestId, request.method(), request.url()));
                Response response = chain.proceed(request);
                log.debug(String.format("requestId: %s, resp: %s", requestId, response.code()));
                return response;
            })
            .build();

    private Map<String, Token> credentials = new ConcurrentHashMap<>();

    private HttpClient() {}

    public static HttpClient getInstance() {
        if (instance == null) {
            instance = new HttpClient();
        }
        return instance;
    }

    public HttpClient auth(Token token) {
        credentials.put(domainFrom(token.getEndpoint()), token);
        return this;
    }

    public Response get(String url) throws IOException {
        return get(url, Headers.of(Collections.emptyMap()));
    }

    public Response get(String url, Headers headers) throws IOException {
        return execute(METHOD_GET, url, headers, null);
    }

    public Response head(String url) throws IOException {
        return head(url, Headers.of(Collections.emptyMap()));
    }

    public Response head(String url, Headers headers) throws IOException {
        return execute(METHOD_HEAD, url, headers, null);
    }

    public Response delete(String url) throws IOException {
        return execute(METHOD_DELETE, url, Headers.of(Collections.emptyMap()), null);
    }

    public Response put(String url, RequestBody requestBody) throws IOException {
        return execute(METHOD_PUT, url, Headers.of(Collections.emptyMap()), requestBody);
    }

    public Response post(String url, RequestBody requestBody) throws IOException {
        return execute(METHOD_POST, url, Headers.of(Collections.emptyMap()), requestBody);
    }

    public Response patch(String url, RequestBody requestBody) throws IOException {
        return execute(METHOD_PATCH, url, Headers.of(Collections.emptyMap()), requestBody);
    }

    private String getToken(Token token) {
        if (token == null) {
            return new DockerHubToken(Constants.ENDPOINT_DEFAULT, null, null).token();
        }
        return token.token();
    }

    private Response execute(String method, String url, Headers headers, RequestBody requestBody) throws IOException {
        String execUrl = url;
        String execMethod = method;
        for (int i = 0; i < 3; i++) {
            Request.Builder requestBuilder = new Request.Builder()
                    .method(execMethod, requestBody)
                    .url(Objects.requireNonNull(execUrl));
            String token = getToken(credentials.get(domainFrom(url)));
            if (token != null && !token.isEmpty()) {
                requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, token);
            }
            headers.forEach(p -> requestBuilder.addHeader(p.getFirst(), p.getSecond()));
            Response response = okHttpClient.newCall(requestBuilder.build()).execute();
            if (response.code() < 300 || response.code() >= 400) return response;
            if (response.code() == 303) {
                execUrl = getLocation(response, url);
                execMethod = METHOD_GET;
                continue;
            }
            execUrl = getLocation(response, url);
        }
        throw new RegistryException("url redirect too much times");
    }


    public String getLocation(Response response, String url) {
        String str = response.header(HttpHeaders.LOCATION);
        if (str == null || str.isEmpty()) {
            throw new NullPointerException("location not found in headers");
        }
        if (str.startsWith(Constants.SCHEMA_HTTP)) {
            return str;
        }
        return String.format("%s//%s%s", url.replaceAll(URL_SUFFIX, ""), domainFrom(url), str);
    }

    public String domainFrom(String url) {
        return url.replaceAll(URL_PREFIX, "").replaceAll(URL_SUFFIX, "");
    }
}
