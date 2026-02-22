package com.scholar.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class AudioWebSocketHandler extends AbstractWebSocketHandler {

    private static final Map<String, List<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("roomId=") && query.contains("userName=")) {
            // URL থেকে আইডি এবং ইউজারের নাম বের করা
            String[] params = query.split("&");
            String roomId = params[0].split("=")[1];
            String userName = java.net.URLDecoder.decode(params[1].split("=")[1], "UTF-8");

            session.getAttributes().put("roomId", roomId);
            session.getAttributes().put("userName", userName);

            rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(session);
            System.out.println("🎙️ " + userName + " joined Audio Room: " + roomId);

            // নতুন কেউ আসলে সবাইকে আপডেট লিস্ট পাঠিয়ে দেওয়া
            broadcastActiveUsers(roomId);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        String roomId = (String) session.getAttributes().get("roomId");
        List<WebSocketSession> roomSessions = rooms.get(roomId);

        if (roomSessions != null) {
            for (WebSocketSession s : roomSessions) {
                if (s.isOpen() && !session.getId().equals(s.getId())) {
                    s.sendMessage(message);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = (String) session.getAttributes().get("roomId");
        if (roomId != null && rooms.containsKey(roomId)) {
            rooms.get(roomId).remove(session);
            // কেউ লিভ নিলে আবার সবাইকে আপডেট লিস্ট পাঠানো
            broadcastActiveUsers(roomId);
        }
    }

    // 🌟 এই মেথডটি রুমে থাকা সব ইউজারের নাম সবাইকে পাঠায়
    private void broadcastActiveUsers(String roomId) {
        List<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) return;

        String activeUsers = roomSessions.stream()
                .filter(WebSocketSession::isOpen)
                .map(s -> (String) s.getAttributes().get("userName"))
                .collect(Collectors.joining(", "));

        TextMessage msg = new TextMessage("USERS:" + activeUsers);
        for (WebSocketSession s : roomSessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(msg);
                } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }
}