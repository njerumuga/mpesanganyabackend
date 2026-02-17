package com.nganyaexperience.backend.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DarajaConfig {

    @Value("${DARAJA_ENV:sandbox}")
    public String env;

    @Value("${DARAJA_CONSUMER_KEY:}")
    public String consumerKey;

    @Value("${DARAJA_CONSUMER_SECRET:}")
    public String consumerSecret;

    @Value("${DARAJA_SHORTCODE:174379}")
    public String shortcode;

    @Value("${DARAJA_PASSKEY:}")
    public String passkey;

    @Value("${DARAJA_CALLBACK_URL:}")
    public String callbackUrl;

    @Value("${DARAJA_TRANSACTION_TYPE:CustomerPayBillOnline}")
    public String transactionType;

    @Value("${DARAJA_PARTYB:174379}")
    public String partyB;

    public String baseUrl() {
        // sandbox.safaricom.co.ke vs api.safaricom.co.ke
        return "production".equalsIgnoreCase(env)
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }
}
