package ru.retailhub.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public class ProxyHandler {

    private final WebClient.Builder webClientBuilder;

    public ProxyHandler(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<ServerResponse> forward(ServerRequest request, String targetBaseUrl) {
        String path = request.path();
        String query = request.uri().getRawQuery();
        String url = targetBaseUrl + path + (query != null ? "?" + query : "");

        WebClient client = webClientBuilder.build();
        HttpMethod method = request.method() != null ? request.method() : HttpMethod.GET;

        WebClient.RequestBodySpec spec = client.method(method).uri(url)
                .headers(h -> request.headers().asHttpHeaders().forEach((name, values) -> {
                    if (!name.equalsIgnoreCase(HttpHeaders.HOST)
                            && !name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                        h.addAll(name, values);
                    }
                }));

        return request.bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> {
                    WebClient.RequestHeadersSpec<?> ready =
                            body.length > 0 ? spec.body(BodyInserters.fromValue(body)) : spec;

                    return ready.exchangeToMono(resp ->
                            resp.bodyToMono(byte[].class)
                                    .defaultIfEmpty(new byte[0])
                                    .flatMap(respBody -> ServerResponse.status(resp.statusCode())
                                            .headers(h -> resp.headers().asHttpHeaders().forEach((n, v) -> {
                                                if (!n.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                                                    h.addAll(n, v);
                                                }
                                            }))
                                            .bodyValue(respBody)));
                });
    }
}
