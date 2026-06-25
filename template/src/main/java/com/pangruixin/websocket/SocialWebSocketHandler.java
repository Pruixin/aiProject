package com.pangruixin.websocket;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Component
public class SocialWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_ID_ATTR = "socialUserId";

    @Autowired
    private SocialSocketService socialSocketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = resolveUserId(session.getUri());
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        session.getAttributes().put(USER_ID_ATTR, userId);
        socialSocketService.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 当前仅用于服务端推送，无需处理客户端消息。
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        socialSocketService.unregister(getUserId(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        socialSocketService.unregister(getUserId(session), session);
    }

    private Long getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(USER_ID_ATTR);
        return userId instanceof Long ? (Long) userId : null;
    }

    private Long resolveUserId(URI uri) {
        if (uri == null) {
            return null;
        }
        List<String> values = UriComponentsBuilder.fromUri(uri).build().getQueryParams().get("token");
        if (values == null || values.isEmpty() || !StringUtils.hasText(values.get(0))) {
            return null;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(values.get(0));
            return loginId == null ? null : Long.valueOf(String.valueOf(loginId));
        } catch (NotLoginException ignored) {
            return null;
        }
    }
}
