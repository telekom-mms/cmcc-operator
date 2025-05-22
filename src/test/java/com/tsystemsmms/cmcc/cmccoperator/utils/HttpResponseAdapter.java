package com.tsystemsmms.cmcc.cmccoperator.utils;

import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.client.http.HttpResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HttpResponse implementation wrapping an existing HttpResponse with a processed body.
 * <p>
 * Allows for changing the body type - there is further redesign that could be done here
 * @param <T> the type of the body.
 */
public class HttpResponseAdapter implements HttpResponse<byte[]> {

    private final int code;
    private String body = "{}";

    public HttpResponseAdapter(int code) {
        this.code = code;
    }

    public HttpResponseAdapter(int code, String body) {
        this.code = code;
        this.body = body;
    }

    @Override
    public List<String> headers(String key) {
        return List.of();
    }

    @Override
    public Map<String, List<String>> headers() {
        return Map.of();
    }

    @Override
    public int code() {
        return this.code;
    }

    @Override
    public byte[] body() {
        return this.body.getBytes();
    }

    @Override
    public HttpRequest request() {
        return null;
    }

    @Override
    public Optional<HttpResponse<?>> previousResponse() {
        return Optional.empty();
    }
}
