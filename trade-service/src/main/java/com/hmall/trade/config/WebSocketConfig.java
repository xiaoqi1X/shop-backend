package com.hmall.trade.config;

import com.hmall.trade.websocket.SeckillResultWebSocketHandler;
import com.hmall.trade.websocket.UserInfoHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final SeckillResultWebSocketHandler handler;
    private final UserInfoHandshakeInterceptor interceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/seckill")
                .addInterceptors(interceptor)
                .setAllowedOrigins("*");
    }
}
