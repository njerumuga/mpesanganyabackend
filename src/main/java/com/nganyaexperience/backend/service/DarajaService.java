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
        // Daraja base URLs
        return ("production".equalsIgnoreCase(env))
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }

    public String getAccessToken() {
        if (consumerKey == null || consumerKey.isBlank() || consumerSecret == null || consumerSecret.isBlank()) {
            throw new RuntimeException("Missing MPESA_CONSUMER_KEY / MPESA_CONSUMER_SECRET");
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
     * STK Push request.
     *
     * @param accessToken  Daraja access token
     * @param phone254     Customer phone in 2547xxxxxxxx format
     * @param amount       Amount (integer KES is recommended by Daraja)
     * @param shortcodeOrNull BusinessShortCode (Paybill shortcode OR Till number). If blank, defaultShortcode is used.
     * @param partyBOrNull PartyB (usually same as shortcode). If blank, shortcode is used.
     * @param transactionTypeOrNull CustomerPayBillOnline OR CustomerBuyGoodsOnline. If blank, CustomerPayBillOnline is used.
     * @param accountReference Account reference
     * @param transactionDesc Description
     */
    public Map<String, Object> stkPush(
            String accessToken,
            String phone254,
            int amount,
            String shortcodeOrNull,
            String partyBOrNull,
            String transactionTypeOrNull,
            String accountReference,
            String transactionDesc
    ) {
        if (passkey == null || passkey.isBlank()) {
            throw new RuntimeException("Missing MPESA_PASSKEY");
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new RuntimeException("Missing MPESA_CALLBACK_URL");
        }

        String shortcode = (shortcodeOrNull == null || shortcodeOrNull.isBlank())
                ? defaultShortcode
                : shortcodeOrNull;

        String partyB = (partyBOrNull == null || partyBOrNull.isBlank()) ? shortcode : partyBOrNull;
        String txType = (transactionTypeOrNull == null || transactionTypeOrNull.isBlank())
                ? "CustomerPayBillOnline"
                : transactionTypeOrNull;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder()
                .encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", txType);
        payload.put("Amount", amount);
        payload.put("PartyA", phone254);
        payload.put("PartyB", partyB);
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

        // return as map
        Map<String, Object> out = new HashMap<>();
        for (Object k : body.keySet()) {
            out.put(String.valueOf(k), body.get(k));
        }
        return out;
    }
}
