package com.scholar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AudioWebSocketHandler audioHandler;

    public WebSocketConfig(AudioWebSocketHandler audioHandler) {
        this.audioHandler = audioHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // জাভাএফএক্স এই লিংকে কানেক্ট করবে: ws://localhost:8080/audio-stream?roomId=...
        registry.addHandler(audioHandler, "/audio-stream").setAllowedOrigins("*");
    }
}