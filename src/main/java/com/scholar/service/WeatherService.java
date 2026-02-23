package com.scholar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

@Service
public class WeatherService {

    // 🌟 ক্লাইমেটের বিস্তারিত ডেটা রাখার রেকর্ড
    public record DailyWeather(String maxTemp, String minTemp, String rainChance, 
                               String uvIndex, String windSpeed, String sunrise, String sunset, 
                               String emoji, String condition) {}

    private Map<String, DailyWeather> currentForecast = new HashMap<>();
    
    // 💾 লোকেশন সেভ করে রাখার জন্য Java Preferences
    private Preferences prefs = Preferences.userNodeForPackage(WeatherService.class);

    // সেভ করা লোকেশন আছে কি না চেক করা
    public boolean hasSavedLocation() {
        return prefs.getDouble("saved_lat", -999) != -999;
    }

    public double[] getSavedLocation() {
        return new double[]{prefs.getDouble("saved_lat", 23.8103), prefs.getDouble("saved_lng", 90.4125)};
    }

    // 📍 ১. ইউজারের আইপি দিয়ে লোকেশন বের করা এবং সেভ করা
    public double[] detectAndSaveLocation() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://get.geojs.io/v1/ip/geo.json")).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            if (json.contains("\"latitude\":") && json.contains("\"longitude\":")) {
                double lat = Double.parseDouble(json.split("\"latitude\":\"")[1].split("\"")[0]);
                double lon = Double.parseDouble(json.split("\"longitude\":\"")[1].split("\"")[0]);
                
                // 💾 লোকেশন সেভ করা হচ্ছে
                prefs.putDouble("saved_lat", lat);
                prefs.putDouble("saved_lng", lon);
                
                System.out.println("📍 Precise Location Saved: Lat: " + lat + ", Lng: " + lon);
                return new double[]{lat, lon};
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to detect location: " + e.getMessage());
        }
        return getSavedLocation(); // ফেইল করলে সেভ করা বা ডিফল্ট লোকেশন দেবে
    }

    // 🌦️ ২. বিস্তারিত ক্লাইমেট ডেটা আনা
    public Map<String, DailyWeather> fetchWeeklyForecast(double lat, double lng) {
        currentForecast.clear();

        try {
            // Open-Meteo থেকে সব ক্লাইমেট ডেটা (UV, Wind, Sunrise, Sunset) আনা হচ্ছে
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng + 
                         "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,windspeed_10m_max,sunrise,sunset,weathercode&timezone=auto";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // ObjectMapper দিয়ে JSON সুন্দরভাবে পড়া হচ্ছে
            ObjectMapper mapper = new ObjectMapper();
            JsonNode daily = mapper.readTree(response.body()).path("daily");

            if (!daily.isMissingNode()) {
                for (int i = 0; i < daily.path("time").size(); i++) {
                    String date = daily.path("time").get(i).asText();
                    String maxT = Math.round(daily.path("temperature_2m_max").get(i).asDouble()) + "°C";
                    String minT = Math.round(daily.path("temperature_2m_min").get(i).asDouble()) + "°C";
                    String rain = daily.path("precipitation_probability_max").get(i).asText() + "%";
                    String uv = daily.path("uv_index_max").get(i).asText();
                    String wind = daily.path("windspeed_10m_max").get(i).asText() + " km/h";
                    
                    // Sunrise/Sunset এর টাইম ফরম্যাট ঠিক করা (2026-02-22T06:15 -> 06:15)
                    String sunrise = daily.path("sunrise").get(i).asText().substring(11);
                    String sunset = daily.path("sunset").get(i).asText().substring(11);
                    
                    int code = daily.path("weathercode").get(i).asInt();
                    String condition = getConditionFromCode(code);
                    String emoji = getEmojiForCondition(condition);

                    currentForecast.put(date, new DailyWeather(maxT, minT, rain, uv, wind, sunrise, sunset, emoji, condition));
                }
                System.out.println("✅ Ultimate Climate Data Fetched Successfully!");
            }
        } catch (Exception e) {
            System.err.println("❌ API fetch failed: " + e.getMessage());
        }
        return currentForecast;
    }

    // কন্ডিশন এবং ইমোজির লজিক আগের মতোই
    private String getConditionFromCode(int code) {
        if (code == 0) return "Clear sky";
        if (code >= 1 && code <= 3) return "Partly cloudy";
        if (code >= 45 && code <= 48) return "Fog";
        if (code >= 51 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Cloudy";
    }

    private String getEmojiForCondition(String condition) {
        String lower = condition.toLowerCase();
        if (lower.contains("clear")) return "☀️";
        if (lower.contains("rain") || lower.contains("shower")) return "🌧️";
        if (lower.contains("cloud")) return "☁️";
        if (lower.contains("snow")) return "❄️";
        if (lower.contains("thunder") || lower.contains("storm")) return "⛈️";
        return "🌤️";
    }
}