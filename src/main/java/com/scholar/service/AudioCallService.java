package com.scholar.service;

import org.springframework.stereotype.Service;
import javax.sound.sampled.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

@Service
public class AudioCallService {

    private TargetDataLine mic;
    private SourceDataLine speaker;
    private WebSocket webSocket;
    private boolean inCall = false;
    private boolean isMuted = false; // 🌟 মিউট ট্র্যাক করার জন্য

    public void joinVoiceChannel(String roomId, String userName, Consumer<String> onUsersUpdate) {
        if (inCall) return;
        isMuted = false; 

        try {
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, true);

            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            mic = (TargetDataLine) AudioSystem.getLine(micInfo);
            mic.open(format); mic.start();

            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speaker.open(format); speaker.start();

            // লিংকের সাথে নিজের নাম যুক্ত করে সার্ভারে কানেক্ট করা
            String encodedName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
            String wsUrl = "ws://localhost:8080/audio-stream?roomId=" + roomId + "&userName=" + encodedName;

            HttpClient client = HttpClient.newHttpClient();
            webSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        
                        @Override
                        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                            byte[] audioBytes = new byte[data.remaining()];
                            data.get(audioBytes);
                            speaker.write(audioBytes, 0, audioBytes.length);
                            return WebSocket.Listener.super.onBinary(webSocket, data, last);
                        }

                        // 🌟 সার্ভার থেকে পাঠানো Active Users এর টেক্সট মেসেজ ধরা
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            String msg = data.toString();
                            if (msg.startsWith("USERS:")) {
                                onUsersUpdate.accept(msg.replace("USERS:", ""));
                            }
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }
                    }).join();

            inCall = true;

            // মাইক্রোফোন লজিক
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (inCall) {
                    int bytesRead = mic.read(buffer, 0, buffer.length);
                    // 🌟 Mute করা থাকলে সার্ভারে ডাটা পাঠাবে না!
                    if (bytesRead > 0 && webSocket != null && !isMuted) {
                        webSocket.sendBinary(ByteBuffer.wrap(buffer, 0, bytesRead), true);
                    }
                }
            }).start();

        } catch (Exception e) { e.printStackTrace(); }
    }

    public void leaveVoiceChannel() {
        inCall = false;
        if (mic != null) { mic.stop(); mic.close(); }
        if (speaker != null) { speaker.stop(); speaker.close(); }
        if (webSocket != null) { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Left room"); }
    }

    // 🌟 মিউট এবং আনমিউট টগল করার মেথড
    public void toggleMute() { isMuted = !isMuted; }
    public boolean isMuted() { return isMuted; }
}