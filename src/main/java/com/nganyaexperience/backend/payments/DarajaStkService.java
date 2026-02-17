package com.nganyaexperience.backend.payments;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DarajaStkService {

    private final DarajaConfig cfg;
    private final AccessTokenService tokens;
    private final RestTemplate rest = new RestTemplate();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public DarajaStkService(DarajaConfig cfg, AccessTokenService tokens) {
        this.cfg = cfg;
        this.tokens = tokens;
    }

    public StkPushResponse stkPush(String phone254, int amount, String accountRef, String desc) {
        String timestamp = LocalDateTime.now().format(TS);
        String password = Base64.getEncoder().encodeToString(
                (cfg.shortcode + cfg.passkey + timestamp).getBytes(StandardCharsets.UTF_8)
        );

        String url = cfg.baseUrl() + "/mpesa/stkpush/v1/processrequest";
        String token = tokens.getAccessToken();

        StkPushRequest body = new StkPushRequest();
        body.BusinessShortCode = cfg.shortcode;
        body.Password = password;
        body.Timestamp = timestamp;
        body.TransactionType = cfg.transactionType; // CustomerPayBillOnline (sandbox), CustomerBuyGoodsOnline (till)
        body.Amount = amount;
        body.PartyA = phone254;
        body.PartyB = cfg.partyB;
        body.PhoneNumber = phone254;
        body.CallBackURL = cfg.callbackUrl;
        body.AccountReference = accountRef;
        body.TransactionDesc = desc;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<StkPushRequest> req = new HttpEntity<>(body, headers);

        ResponseEntity<StkPushResponse> res = rest.exchange(url, HttpMethod.POST, req, StkPushResponse.class);
        return res.getBody();
    }

    public Object stkQuery(String checkoutRequestId) {
        String timestamp = LocalDateTime.now().format(TS);
        String password = Base64.getEncoder().encodeToString(
                (cfg.shortcode + cfg.passkey + timestamp).getBytes(StandardCharsets.UTF_8)
        );

        String url = cfg.baseUrl() + "/mpesa/stkpushquery/v1/query";
        String token = tokens.getAccessToken();

        var payload = new java.util.HashMap<String, Object>();
        payload.put("BusinessShortCode", cfg.shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("CheckoutRequestID", checkoutRequestId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Object> req = new HttpEntity<>(payload, headers);

        return rest.exchange(url, HttpMethod.POST, req, Object.class).getBody();
    }

    public static class StkPushRequest {
        public String BusinessShortCode;
        public String Password;
        public String Timestamp;
        public String TransactionType;
        public Integer Amount;
        public String PartyA;
        public String PartyB;
        public String PhoneNumber;
        public String CallBackURL;
        public String AccountReference;
        public String TransactionDesc;
    }

    public static class StkPushResponse {
        public String MerchantRequestID;
        public String CheckoutRequestID;
        public String ResponseCode;
        public String ResponseDescription;
        public String CustomerMessage;
    }
}
