package com.scholar.controller.community;

import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * CommunityRagController — embeds the RAG chat WebView inside Community tab.
 */
@Component
public class CommunityRagController {

    @Autowired private JdbcTemplate jdbc;

    private WebView webView;
    private String currentUserId;

    public void init(WebView view, String userId) {
        this.webView = view;
        this.currentUserId = userId;
        if (webView == null) return;

        URL htmlUrl = getClass().getResource("/static/ai_chat.html");
        if (htmlUrl == null) return;

        webView.getEngine().load(htmlUrl.toExternalForm());
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                injectUserContext();
            }
        });
    }

    private void injectUserContext() {
        if (webView == null) return;
        if (currentUserId != null && !currentUserId.isBlank()) {
            webView.getEngine().executeScript(
                "if(typeof setUserId === 'function') setUserId('" + escape(currentUserId) + "');"
            );
        }

        try {
            List<Map<String, Object>> courses = jdbc.queryForList(
                "SELECT code, title FROM courses ORDER BY code"
            );
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < courses.size(); i++) {
                Map<String, Object> c = courses.get(i);
                json.append("{\"code\":\"")
                    .append(escape(String.valueOf(c.get("code"))))
                    .append("\",\"title\":\"")
                    .append(escape(String.valueOf(c.get("title"))))
                    .append("\"}");
                if (i < courses.size() - 1) json.append(",");
            }
            json.append("]");
            String script = "if(typeof setCourses === 'function') setCourses('"
                          + json.toString().replace("'", "\\'")
                          + "');";
            webView.getEngine().executeScript(script);
        } catch (Exception ignored) {}
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\\\", "\\\\\\\\")
                .replace("\"", "\\\\\"")
                .replace("'", "\\\\'")
                .replace("\n", " ")
                .replace("\r", "");
    }
}
