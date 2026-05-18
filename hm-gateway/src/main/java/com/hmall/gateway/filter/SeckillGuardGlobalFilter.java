package com.hmall.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmall.gateway.config.SeckillGuardProperties;
import com.hmall.gateway.seckill.SeckillGuardOrderForm;
import com.hmall.gateway.seckill.SeckillGuardResult;
import com.hmall.gateway.seckill.SeckillGuardStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SeckillGuardProperties.class)
public class SeckillGuardGlobalFilter implements GlobalFilter, Ordered {

    private static final int DEFAULT_NUM = 1;

    private final SeckillGuardProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!shouldGuard(request)) {
            return chain.filter(exchange);
        }
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(buffer -> guardWithBody(exchange, chain, buffer));
    }

    private Mono<Void> guardWithBody(ServerWebExchange exchange, GatewayFilterChain chain, DataBuffer buffer) {
        byte[] body;
        try {
            int readableBytes = buffer.readableByteCount();
            if (readableBytes <= 0) {
                return reject(exchange, null, null, SeckillGuardStatus.FAILED, "Request body must not be empty");
            }
            if (readableBytes > properties.getMaxBodyBytes()) {
                return reject(exchange, null, null, SeckillGuardStatus.FAILED, "Request body is too large");
            }
            body = new byte[readableBytes];
            buffer.read(body);
        } finally {
            DataBufferUtils.release(buffer);
        }

        SeckillGuardOrderForm form;
        try {
            form = objectMapper.readValue(body, SeckillGuardOrderForm.class);
        } catch (Exception e) {
            log.debug("Reject seckill request because request body cannot be parsed", e);
            return reject(exchange, null, null, SeckillGuardStatus.INVALID_ACTIVITY, "Invalid request body");
        }
        SeckillGuardStatus formStatus = validateForm(form);
        if (formStatus != null) {
            return reject(exchange, form == null ? null : form.getSeckillId(), form == null ? null : form.getItemId(), formStatus, formStatus.getMessage());
        }

        return chain.filter(withCachedBody(exchange, body));
    }

    private SeckillGuardStatus validateForm(SeckillGuardOrderForm form) {
        if (form == null || form.getSeckillId() == null || form.getItemId() == null) {
            return SeckillGuardStatus.INVALID_ACTIVITY;
        }
        int num = form.getNum() == null ? DEFAULT_NUM : form.getNum();
        if (num <= 0) {
            return SeckillGuardStatus.FAILED;
        }
        return null;
    }

    private boolean shouldGuard(ServerHttpRequest request) {
        return properties.isEnabled()
                && request.getMethod() == HttpMethod.POST
                && properties.getOrderPath().equals(request.getPath().pathWithinApplication().value());
    }

    private ServerWebExchange withCachedBody(ServerWebExchange exchange, byte[] body) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                return Flux.just(bufferFactory.wrap(body));
            }

            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.setContentLength(body.length);
                return headers;
            }
        };
        return exchange.mutate().request(decorator).build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, Long seckillId, Long itemId, SeckillGuardStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        SeckillGuardResult result = SeckillGuardResult.of(seckillId, itemId, status.name(), message == null ? status.getMessage() : message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(result);
        } catch (Exception e) {
            bytes = "{\"status\":\"FAILED\",\"message\":\"Seckill failed\"}".getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
