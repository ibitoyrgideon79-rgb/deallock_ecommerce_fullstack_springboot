package com.deallock.backend.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    @Value("${twilio.account-sid:}")
    private String accountSid;
    @Value("${twilio.auth-token:}")
    private String authToken;
    @Value("${twilio.phone-number:}")
    private String fromNumber;
    @Value("${app.admin-phones:}")
    private String adminPhones;

    private boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && fromNumber != null && !fromNumber.isBlank();
    }

    public void sendToUser(String phone, String message) {
        if (phone == null || phone.isBlank()) return;
        send(phone, message);
    }

    public void sendToAdmins(String message) {
        if (adminPhones == null || adminPhones.isBlank()) return;
        List<String> phones = Arrays.stream(adminPhones.split(","))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();
        phones.forEach(p -> send(p, message));
    }

    private void send(String to, String message) {
        if (!isConfigured()) {
            System.out.println("[DEV] SMS not configured. To: " + to + " | " + message);
            return;
        }
        Twilio.init(accountSid, authToken);
        Message.creator(new PhoneNumber(to), new PhoneNumber(fromNumber), message).create();
    }
}
