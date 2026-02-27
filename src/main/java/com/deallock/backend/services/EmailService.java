package com.deallock.backend.services;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${emailjs.service-id:}")
    private String serviceId;
    @Value("${emailjs.template-id:}")
    private String templateId;
    @Value("${emailjs.public-key:}")
    private String publicKey;
    @Value("${emailjs.private-key:}")
    private String privateKey;

    private void send(String to, String subject, String text) {
        if (serviceId == null || serviceId.isBlank()
                || templateId == null || templateId.isBlank()
                || publicKey == null || publicKey.isBlank()) {
            System.out.println("[DEV] EmailJS not configured. To: " + to + " | " + subject + " | " + text);
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("to_email", to);
        params.put("subject", subject);
        params.put("message", text);

        Map<String, Object> payload = new HashMap<>();
        payload.put("service_id", serviceId);
        payload.put("template_id", templateId);
        payload.put("user_id", publicKey);
        payload.put("template_params", params);
        if (privateKey != null && !privateKey.isBlank()) {
            payload.put("accessToken", privateKey);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            restTemplate.postForEntity("https://api.emailjs.com/api/v1.0/email/send", entity, String.class);
        } catch (Exception ex) {
            System.out.println("[DEV] EmailJS send failed: " + ex.getMessage());
        }
    }

    public void sendOtp(String email, String otp) {
        send(email, "Your OTP Code", "Your OTP is: " + otp);
    }

    public void sendActivationLink(String email, String link) {
        send(email, "Activate your account", "Click: " + link);
    }

    public void sendPasswordResetLink(String email, String link) {
        send(email, "Reset your password", "Click: " + link);
    }

    public void sendDealCreatedToAdmin(String email, String details) {
        send(email, "New Deal Created", details);
    }

    public void sendDealCreatedToUser(String email, String details) {
        send(email, "Your Deal Was Created", details);
    }

    public void sendDealApprovedToUser(String email, String details) {
        send(email, "Your Deal Was Approved", details);
    }

    public void sendDealApprovedToAdmin(String email, String details) {
        send(email, "Deal Approved", details);
    }
}
