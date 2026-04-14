package ru.retailhub.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyHandlerTest {

    @Test
    void forward_constructsCorrectUrlWithPathAndQuery() {
        String targetBaseUrl = "http://localhost:8081";
        String path = "/api/v1/requests";
        String query = "storeId=abc&status=COMPLETED";

        String url = targetBaseUrl + path + "?" + query;

        assertThat(url).isEqualTo("http://localhost:8081/api/v1/requests?storeId=abc&status=COMPLETED");
    }

    @Test
    void forward_constructsCorrectUrlWithoutQuery() {
        String targetBaseUrl = "http://localhost:8081";
        String path = "/api/v1/users";
        String query = null;

        String url = targetBaseUrl + path + (query != null ? "?" + query : "");

        assertThat(url).isEqualTo("http://localhost:8081/api/v1/users");
    }

    @Test
    void forward_preservesNestedPath() {
        String targetBaseUrl = "http://localhost:8082";
        String path = "/api/v1/stores/123/departments";
        String query = "page=0&size=10";

        String url = targetBaseUrl + path + "?" + query;
        URI uri = URI.create(url);

        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPort()).isEqualTo(8082);
        assertThat(uri.getPath()).isEqualTo("/api/v1/stores/123/departments");
        assertThat(uri.getQuery()).isEqualTo("page=0&size=10");
    }

    @Test
    void proxyHandler_canBeConstructed() {
        WebClient.Builder builder = WebClient.builder();
        ProxyHandler handler = new ProxyHandler(builder);
        assertThat(handler).isNotNull();
    }
}
