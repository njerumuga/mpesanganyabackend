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

    @Data
    public static class StkPushRequest {
        private Long bookingId;
        private String phoneNumber; // 2547...
    }

    @PostMapping("/stk-push")
    public ResponseEntity<?> stkPush(@RequestBody StkPushRequest req) {
        if (req.getBookingId() == null) return ResponseEntity.badRequest().body("bookingId required");
        if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank()) {
            return ResponseEntity.badRequest().body("phoneNumber required");
        }

        Booking booking = bookingRepository.findById(req.getBookingId())
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getPaymentStatus() == Booking.PaymentStatus.PAID) {
            return ResponseEntity.ok(Map.of(
                    "status", "PAID",
                    "ticketCode", booking.getTicketCode(),
                    "bookingId", booking.getId()
            ));
        }

        double amount = booking.getTicketType().getPrice();

        // Paybill shortcode stored in event.paymentNumber
        String shortcode = booking.getEvent().getPaymentNumber();
        String accountRef = "BOOK-" + booking.getId();
        String desc = "Nganya Experience - " + booking.getEvent().getTitle();

        String token = darajaService.getAccessToken();
        Map<String, Object> resp = darajaService.stkPush(token, normalizePhone(req.getPhoneNumber()), amount, shortcode, accountRef, desc);

        String checkoutRequestId = String.valueOf(resp.get("CheckoutRequestID"));
        String merchantRequestId = String.valueOf(resp.get("MerchantRequestID"));

        MpesaPayment payment = MpesaPayment.builder()
                .booking(booking)
                .phone(normalizePhone(req.getPhoneNumber()))
                .amount(amount)
                .checkoutRequestId(checkoutRequestId)
                .merchantRequestId(merchantRequestId)
                .status(MpesaPayment.Status.PENDING)
                .build();
        paymentRepository.save(payment);

        return ResponseEntity.ok(Map.of(
                "status", "PENDING",
                "checkoutRequestId", checkoutRequestId,
                "merchantRequestId", merchantRequestId,
                "bookingId", booking.getId(),
                "daraja", resp
        ));
    }

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
     * Safaricom calls this URL after STK push.
     */
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<?> callback(@RequestBody Map<String, Object> payload) throws Exception {
        // Safaricom sends: { Body: { stkCallback: { MerchantRequestID, CheckoutRequestID, ResultCode, ResultDesc, CallbackMetadata }}}
        Map body = (Map) payload.get("Body");
        if (body == null) return ResponseEntity.ok(Map.of("ok", true));
        Map stk = (Map) body.get("stkCallback");
        if (stk == null) return ResponseEntity.ok(Map.of("ok", true));

        String checkoutRequestId = String.valueOf(stk.get("CheckoutRequestID"));
        Integer resultCode = stk.get("ResultCode") == null ? null : Integer.parseInt(String.valueOf(stk.get("ResultCode")));

        MpesaPayment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElse(null);
        if (payment == null) {
            // Unknown checkout id - still return 200
            return ResponseEntity.ok(Map.of("ok", true));
        }

        payment.setRawCallback(objectMapper.writeValueAsString(payload));

        if (resultCode != null && resultCode == 0) {
            // success: try get receipt number
            String receipt = extractReceipt(stk);
            payment.setMpesaReceipt(receipt);
            payment.setStatus(MpesaPayment.Status.PAID);
            paymentRepository.save(payment);

            // Confirm booking, issue ticket, reduce seat
            bookingService.confirmPaid(payment.getBooking().getId());
        } else {
            payment.setStatus(MpesaPayment.Status.FAILED);
            paymentRepository.save(payment);
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String normalizePhone(String phone) {
        String p = phone.trim();
        // Accept 07.., 7.., +2547.., 2547..
        if (p.startsWith("+")) p = p.substring(1);
        if (p.startsWith("0") && p.length() == 10) {
            return "254" + p.substring(1);
        }
        if (p.startsWith("7") && p.length() == 9) {
            return "254" + p;
        }
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
