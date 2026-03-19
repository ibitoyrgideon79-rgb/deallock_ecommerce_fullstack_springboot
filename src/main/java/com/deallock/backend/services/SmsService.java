package com.deallock.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper;

    @Value("${termii.base-url:https://api.ng.termii.com/api}")
    private String baseUrl;
    @Value("${termii.api-key:}")
    private String apiKey;
    @Value("${termii.sender-id:}")
    private String senderId;
    @Value("${termii.sms-channel:dnd}")
    private String smsChannel;
    @Value("${termii.whatsapp-sender:}")
    private String whatsappSender;
    @Value("${app.admin-phones:}")
    private String adminPhones;

    public SmsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private boolean isSmsConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && senderId != null && !senderId.isBlank();
    }

    private boolean isWhatsAppConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && whatsappSender != null && !whatsappSender.isBlank();
    }

    public void sendToUser(String phone, String message) {
        if (phone == null || phone.isBlank()) return;
        sendSms(phone, message);
    }

    public void sendWhatsAppToUser(String phone, String message) {
        if (phone == null || phone.isBlank()) return;
        sendWhatsApp(phone, message);
    }

    public void sendToAdmins(String message) {
        if (adminPhones == null || adminPhones.isBlank()) return;
        List<String> phones = Arrays.stream(adminPhones.split(","))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();
        phones.forEach(p -> sendSms(p, message));
    }

    public void sendWhatsAppToAdmins(String message) {
        if (adminPhones == null || adminPhones.isBlank()) return;
        List<String> phones = Arrays.stream(adminPhones.split(","))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();
        phones.forEach(p -> sendWhatsApp(p, message));
    }

    private void sendSms(String to, String message) {
        if (!isSmsConfigured()) {
            System.out.println("[DEV] Termii SMS not configured. To: " + to + " | " + message);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "to", to,
                    "from", senderId,
                    "sms", message,
                    "type", "plain",
                    "channel", smsChannel,
                    "api_key", apiKey
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/sms/send"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            System.out.println("[WARN] Termii SMS failed: " + ex.getMessage());
        }
    }

    private void sendWhatsApp(String to, String message) {
        if (!isWhatsAppConfigured()) {
            System.out.println("[DEV] Termii WhatsApp not configured. To: " + to + " | " + message);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "to", to,
                    "from", whatsappSender,
                    "message", message,
                    "api_key", apiKey
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/whatsapp/send"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            System.out.println("[WARN] Termii WhatsApp failed: " + ex.getMessage());
        }
    }
}
