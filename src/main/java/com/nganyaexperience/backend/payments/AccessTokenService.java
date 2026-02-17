package com.nganyaexperience.backend.payments;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AccessTokenService {

    private final DarajaConfig cfg;
    private final RestTemplate rest = new RestTemplate();

    public AccessTokenService(DarajaConfig cfg) {
        this.cfg = cfg;
    }

    public String getAccessToken() {
        String url = cfg.baseUrl() + "/oauth/v1/generate?grant_type=client_credentials";

        String basic = cfg.consumerKey + ":" + cfg.consumerSecret;
        String auth = "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);

        HttpEntity<Void> req = new HttpEntity<>(headers);

        ResponseEntity<TokenResponse> res =
                rest.exchange(url, HttpMethod.GET, req, TokenResponse.class);

        if (res.getBody() == null || res.getBody().access_token == null) {
            throw new RuntimeException("Failed to get access token from Daraja");
        }
        return res.getBody().access_token;
    }

    public static class TokenResponse {
        public String access_token;
        public String expires_in;
    }
}
