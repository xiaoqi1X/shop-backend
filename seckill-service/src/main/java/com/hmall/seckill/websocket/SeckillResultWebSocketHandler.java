package com.hmall.seckill.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class SeckillResultWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<Long, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        SESSIONS.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("Opened seckill websocket session, userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Seckill websocket error, sessionId={}", session == null ? null : session.getId(), exception);
        remove(session);
    }

    public static boolean send(Long userId, String payload) {
        Set<WebSocketSession> sessions = SESSIONS.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        boolean delivered = false;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                    delivered = true;
                } catch (Exception e) {
                    log.warn("Failed to send seckill websocket message, userId={}, sessionId={}",
                            userId, session.getId(), e);
                }
            }
        }
        return delivered;
    }

    private static void remove(WebSocketSession session) {
        if (session == null) {
            return;
        }
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = SESSIONS.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            SESSIONS.remove(userId);
        }
        log.debug("Closed seckill websocket session, userId={}, sessionId={}", userId, session.getId());
    }
}
