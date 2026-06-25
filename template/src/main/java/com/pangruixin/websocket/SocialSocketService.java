package com.pangruixin.websocket;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocialSocketService {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
    }

    public void notifyUsers(List<Long> userIds, String type, String reason) {
        notifyUsers(userIds, type, reason, Map.of());
    }

    public void notifyUsers(List<Long> userIds, String type, String reason, Map<String, Object> extra) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
        payloadMap.put("type", type);
        payloadMap.put("reason", reason);
        payloadMap.putAll(extra);
        String payload = JSON.toJSONString(payloadMap);
        TextMessage message = new TextMessage(payload);
        userIds.stream().distinct().forEach(userId -> sendToUser(userId, message));
    }

    private void sendToUser(Long userId, TextMessage message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException ignored) {
            }
        }
    }
}
