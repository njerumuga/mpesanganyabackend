package com.nganyaexperience.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DarajaService {

    // Keep your existing property names (mpesa.*) so Render vars can match
    @Value("${mpesa.env:sandbox}")
    private String env;

    @Value("${mpesa.consumer-key:}")
    private String consumerKey;

    @Value("${mpesa.consumer-secret:}")
    private String consumerSecret;

    @Value("${mpesa.passkey:}")
    private String passkey;

    @Value("${mpesa.callback-url:}")
    private String callbackUrl;

    @Value("${mpesa.default-shortcode:174379}")
    private String defaultShortcode;

    private final RestTemplate restTemplate = new RestTemplate();

    private String baseUrl() {
        return ("production".equalsIgnoreCase(env))
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }

    public String getAccessToken() {
        if (consumerKey == null || consumerKey.isBlank() || consumerSecret == null || consumerSecret.isBlank()) {
            throw new RuntimeException("Missing mpesa.consumer-key / mpesa.consumer-secret");
        }

        String credentials = consumerKey + ":" + consumerSecret;
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + basic);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/oauth/v1/generate?grant_type=client_credentials",
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map body = resp.getBody();
        if (body == null || body.get("access_token") == null) {
            throw new RuntimeException("Failed to obtain Daraja access token");
        }
        return String.valueOf(body.get("access_token"));
    }

    /**
     * UPDATED SIGNATURE (8 args) to match your controller call.
     *
     * @param businessShortCode  STK push shortcode (sandbox: 174379; production till: 7821537)
     * @param partyB             receiving shortcode (same as shortcode for most cases)
     * @param transactionType    CustomerPayBillOnline (sandbox/paybill) OR CustomerBuyGoodsOnline (till)
     */
    public Map<String, Object> stkPush(
            String accessToken,
            String phone254,
            int amount,
            String businessShortCode,
            String partyB,
            String transactionType,
            String accountReference,
            String transactionDesc
    ) {
        if (passkey == null || passkey.isBlank()) {
            throw new RuntimeException("Missing mpesa.passkey");
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new RuntimeException("Missing mpesa.callback-url");
        }

        String shortcode = (businessShortCode == null || businessShortCode.isBlank())
                ? defaultShortcode
                : businessShortCode;

        String pb = (partyB == null || partyB.isBlank())
                ? shortcode
                : partyB;

        String txType = (transactionType == null || transactionType.isBlank())
                ? "CustomerPayBillOnline"
                : transactionType;

        // Daraja password: Base64Encode(Shortcode + Passkey + Timestamp)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder()
                .encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", txType);
        payload.put("Amount", amount); // already int in controller
        payload.put("PartyA", phone254);
        payload.put("PartyB", pb);
        payload.put("PhoneNumber", phone254);
        payload.put("CallBackURL", callbackUrl);
        payload.put("AccountReference", accountReference);
        payload.put("TransactionDesc", transactionDesc);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/mpesa/stkpush/v1/processrequest",
                HttpMethod.POST,
                entity,
                Map.class
        );

        Map body = resp.getBody();
        if (body == null) throw new RuntimeException("Empty Daraja STK response");

        // Convert to Map<String,Object>
        Map<String, Object> out = new HashMap<>();
        for (Object k : body.keySet()) {
            out.put(String.valueOf(k), body.get(k));
        }
        return out;
    }
}
