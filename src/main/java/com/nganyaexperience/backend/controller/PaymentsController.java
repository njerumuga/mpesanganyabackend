package com.nganyaexperience.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nganyaexperience.backend.entity.Booking;
import com.nganyaexperience.backend.entity.MpesaPayment;
import com.nganyaexperience.backend.repository.BookingRepository;
import com.nganyaexperience.backend.repository.MpesaPaymentRepository;
import com.nganyaexperience.backend.service.BookingService;
import com.nganyaexperience.backend.service.DarajaService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentsController {

    private final DarajaService darajaService;
    private final BookingRepository bookingRepository;
    private final MpesaPaymentRepository paymentRepository;
    private final BookingService bookingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== Daraja config from Render ENV =====
    // Sandbox: 174379
    // Production: your Till 7821537
    @Value("${DARAJA_SHORTCODE:174379}")
    private String darajaShortcode;

    // Sandbox: CustomerPayBillOnline
    // Production Till: CustomerBuyGoodsOnline
    @Value("${DARAJA_TRANSACTION_TYPE:CustomerPayBillOnline}")
    private String transactionType;

    // Sandbox: 174379
    // Production Till: 7821537
    @Value("${DARAJA_PARTYB:174379}")
    private String partyB;

    @Value("${DARAJA_ENV:sandbox}")
    private String darajaEnv;

    @Data
    public static class StkPushRequest {
        private Long bookingId;
        private String phoneNumber; // 2547..., 07..., +2547..., 7...
    }

    /**
     * POST /api/payments/stk-push
     * Creates an STK push prompt on the user's phone.
     */
    @PostMapping("/stk-push")
    public ResponseEntity<?> stkPush(@RequestBody StkPushRequest req) {
        if (req.getBookingId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "bookingId required"));
        }
        if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber required"));
        }

        Booking booking = bookingRepository.findById(req.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // If already paid, return immediately
        if (booking.getPaymentStatus() == Booking.PaymentStatus.PAID) {
            return ResponseEntity.ok(Map.of(
                    "status", "PAID",
                    "ticketCode", booking.getTicketCode(),
                    "bookingId", booking.getId()
            ));
        }

        // Daraja expects whole number amounts
        int amount = (int) Math.round(booking.getTicketType().getPrice());
        if (amount <= 0) amount = 1;

        String phone254 = normalizePhone(req.getPhoneNumber());
        String accountRef = "BOOK-" + booking.getId();
        String desc = "Nganya Experience - " + booking.getEvent().getTitle();

        // IMPORTANT:
        // Do NOT use booking.getEvent().getPaymentNumber() for sandbox.
        // Sandbox ONLY works with 174379.
        // For production till we switch env vars (shortcode/partyB/type).
        String businessShortcode = darajaShortcode;

        // Token + STK push
        String token = darajaService.getAccessToken();
        Map<String, Object> resp = darajaService.stkPush(
                token,
                phone254,
                amount,
                businessShortcode,
                partyB,
                transactionType,
                accountRef,
                desc
        );

        // Daraja may return errors without CheckoutRequestID, so be defensive
        String checkoutRequestId = resp.get("CheckoutRequestID") == null ? null : String.valueOf(resp.get("CheckoutRequestID"));
        String merchantRequestId = resp.get("MerchantRequestID") == null ? null : String.valueOf(resp.get("MerchantRequestID"));

        // Save payment row
        MpesaPayment payment = MpesaPayment.builder()
                .booking(booking)
                .phone(phone254)
                .amount((double) amount)
                .checkoutRequestId(checkoutRequestId)
                .merchantRequestId(merchantRequestId)
                .status(MpesaPayment.Status.PENDING)
                .build();

        // Save raw response for debugging (in case callback never hits)
        try {
            payment.setRawCallback(objectMapper.writeValueAsString(Map.of("stkPushResponse", resp)));
        } catch (Exception ignored) {}

        paymentRepository.save(payment);

        return ResponseEntity.ok(Map.of(
                "status", "PENDING",
                "env", darajaEnv,
                "checkoutRequestId", checkoutRequestId,
                "merchantRequestId", merchantRequestId,
                "bookingId", booking.getId(),
                "amount", amount,
                "phone", phone254,
                "daraja", resp
        ));
    }

    /**
     * GET /api/payments/status/{checkoutRequestId}
     * Frontend polls this to know if payment succeeded.
     */
    @GetMapping("/status/{checkoutRequestId}")
    public ResponseEntity<?> status(@PathVariable String checkoutRequestId) {
        MpesaPayment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        Booking booking = payment.getBooking();

        return ResponseEntity.ok(Map.of(
                "paymentStatus", payment.getStatus(),
                "bookingPaymentStatus", booking.getPaymentStatus(),
                "ticketCode", booking.getTicketCode(),
                "bookingId", booking.getId()
        ));
    }

    /**
     * POST /api/payments/callback
     * Safaricom calls this after user enters PIN or cancels.
     */
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<?> callback(@RequestBody Map<String, Object> payload) throws Exception {
        // Expected:
        // { Body: { stkCallback: { MerchantRequestID, CheckoutRequestID, ResultCode, ResultDesc, CallbackMetadata }}}

        Map body = (Map) payload.get("Body");
        if (body == null) return ResponseEntity.ok(Map.of("ok", true));

        Map stk = (Map) body.get("stkCallback");
        if (stk == null) return ResponseEntity.ok(Map.of("ok", true));

        String checkoutRequestId = String.valueOf(stk.get("CheckoutRequestID"));
        Integer resultCode = stk.get("ResultCode") == null ? null : Integer.parseInt(String.valueOf(stk.get("ResultCode")));

        MpesaPayment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId).orElse(null);
        if (payment == null) {
            // Unknown checkout id - still return 200 to stop retries
            return ResponseEntity.ok(Map.of("ok", true));
        }

        // Save raw callback JSON
        payment.setRawCallback(objectMapper.writeValueAsString(payload));

        if (resultCode != null && resultCode == 0) {
            // SUCCESS
            String receipt = extractReceipt(stk);
            payment.setMpesaReceipt(receipt);
            payment.setStatus(MpesaPayment.Status.PAID);
            paymentRepository.save(payment);

            // Confirm booking, issue ticket, reduce seat
            bookingService.confirmPaid(payment.getBooking().getId());
        } else {
            // FAILED or CANCELLED
            payment.setStatus(MpesaPayment.Status.FAILED);
            paymentRepository.save(payment);
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ===== Helpers =====

    private static String normalizePhone(String phone) {
        String p = phone.trim().replace(" ", "");
        if (p.startsWith("+")) p = p.substring(1);

        // 07XXXXXXXX -> 2547XXXXXXXX
        if (p.startsWith("0") && p.length() == 10) {
            return "254" + p.substring(1);
        }

        // 7XXXXXXXX -> 2547XXXXXXXX
        if (p.startsWith("7") && p.length() == 9) {
            return "254" + p;
        }

        // already 2547...
        return p;
    }

    private static String extractReceipt(Map stk) {
        try {
            Map meta = (Map) stk.get("CallbackMetadata");
            if (meta == null) return null;

            Object itemsObj = meta.get("Item");
            if (!(itemsObj instanceof java.util.List<?> items)) return null;

            for (Object it : items) {
                if (!(it instanceof Map m)) continue;
                if ("MpesaReceiptNumber".equals(String.valueOf(m.get("Name")))) {
                    Object v = m.get("Value");
                    return v == null ? null : String.valueOf(v);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
