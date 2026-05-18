package com.hmgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hmall.gateway.config.SeckillGuardProperties;
import com.hmall.gateway.filter.SeckillGuardGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillGuardGlobalFilterTest {

    private SeckillGuardProperties properties;
    private ObjectMapper objectMapper;
    private SeckillGuardGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SeckillGuardProperties();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new SeckillGuardGlobalFilter(properties, objectMapper);
    }

    @Test
    void shouldSkipNonOrderRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/seckill/items").build());
        AtomicBoolean called = new AtomicBoolean(false);

        StepVerifier.create(filter.filter(exchange, e -> {
            called.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(called).isTrue();
    }

    @Test
    void shouldRejectEmptyBody() {
        MockServerWebExchange exchange = orderExchange("");

        StepVerifier.create(filter.filter(exchange, neverCalledChain())).verifyComplete();

        assertThat(responseBody(exchange)).contains("\"status\":\"FAILED\"");
        assertThat(responseBody(exchange)).contains("Request body must not be empty");
    }

    @Test
    void shouldRejectInvalidJson() {
        MockServerWebExchange exchange = orderExchange("{bad-json");

        StepVerifier.create(filter.filter(exchange, neverCalledChain())).verifyComplete();

        assertThat(responseBody(exchange)).contains("\"status\":\"INVALID_ACTIVITY\"");
        assertThat(responseBody(exchange)).contains("Invalid request body");
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        MockServerWebExchange exchange = orderExchange("{\"seckillId\":100,\"num\":1}");

        StepVerifier.create(filter.filter(exchange, neverCalledChain())).verifyComplete();

        assertThat(responseBody(exchange)).contains("\"status\":\"INVALID_ACTIVITY\"");
        assertThat(responseBody(exchange)).contains("\"seckillId\":100");
    }

    @Test
    void shouldRejectInvalidNum() {
        MockServerWebExchange exchange = orderExchange("{\"seckillId\":100,\"itemId\":200,\"num\":0}");

        StepVerifier.create(filter.filter(exchange, neverCalledChain())).verifyComplete();

        assertThat(responseBody(exchange)).contains("\"status\":\"FAILED\"");
        assertThat(responseBody(exchange)).contains("\"seckillId\":100");
    }

    @Test
    void shouldRejectTooLargeBody() {
        properties.setMaxBodyBytes(16);
        MockServerWebExchange exchange = orderExchange("{\"seckillId\":100,\"itemId\":200,\"num\":1}");

        StepVerifier.create(filter.filter(exchange, neverCalledChain())).verifyComplete();

        assertThat(responseBody(exchange)).contains("\"status\":\"FAILED\"");
        assertThat(responseBody(exchange)).contains("Request body is too large");
    }

    @Test
    void shouldPassAndReplayBodyWhenFormValid() {
        String body = "{\"seckillId\":100,\"itemId\":200,\"num\":1}";
        MockServerWebExchange exchange = orderExchange(body);
        AtomicReference<String> downstreamBody = new AtomicReference<>();

        GatewayFilterChain chain = e -> DataBufferUtils.join(e.getRequest().getBody())
                .flatMap(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    downstreamBody.set(new String(bytes, StandardCharsets.UTF_8));
                    byte[] response = "{\"status\":\"ACCEPTED\"}".getBytes(StandardCharsets.UTF_8);
                    e.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return e.getResponse().writeWith(Mono.just(e.getResponse().bufferFactory().wrap(response)));
                });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(downstreamBody.get()).isEqualTo(body);
        assertThat(responseBody(exchange)).contains("\"status\":\"ACCEPTED\"");
    }

    private MockServerWebExchange orderExchange(String body) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.POST, "/seckill/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    private GatewayFilterChain neverCalledChain() {
        return e -> Mono.error(new AssertionError("chain should not be called"));
    }

    private String responseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
