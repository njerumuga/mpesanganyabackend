package com.nganyaexperience.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nganyaexperience.backend.entity.Booking;
import com.nganyaexperience.backend.entity.Event;
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
        private String phoneNumber; // 2547... / 07...
    }

    /**
     * Trigger STK Push for a booking.
     * - Works for both PAYBILL (CustomerPayBillOnline) and TILL (CustomerBuyGoodsOnline)
     * - Does NOT reduce seats here; seats are reduced ONLY after callback success.
     */
    @PostMapping("/stk-push")
    public ResponseEntity<?> stkPush(@RequestBody StkPushRequest req) {
        if (req.getBookingId() == null) return ResponseEntity.badRequest().body(Map.of("error", "bookingId required"));
        if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber required"));
        }

        Booking booking = bookingRepository.findById(req.getBookingId()).orElse(null);
        if (booking == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Booking not found", "bookingId", req.getBookingId()));
        }

        // If already paid, return final state.
        if (booking.getPaymentStatus() == Booking.PaymentStatus.PAID) {
            return ResponseEntity.ok(Map.of(
                    "status", "PAID",
                    "ticketCode", booking.getTicketCode(),
                    "bookingId", booking.getId()
            ));
        }

        // Allow retry: move FAILED/CANCELLED back to PENDING
        bookingService.ensurePending(booking.getId());

        Event event = booking.getEvent();
        String phone254 = normalizePhone(req.getPhoneNumber());
        int amount = (int) Math.round(booking.getTicketType().getPrice());

        // Shortcode (Paybill shortcode OR Till number)
        String businessShortcode = event.getPaymentNumber();

        // Transaction type & partyB:
        // - PAYBILL: CustomerPayBillOnline
        // - TILL: CustomerBuyGoodsOnline
        String txType = (event.getPaymentMethod() == Event.PaymentMethod.TILL)
                ? "CustomerBuyGoodsOnline"
                : "CustomerPayBillOnline";

        // Allow override via Render env vars (optional)
        String txTypeOverride = System.getenv("DARAJA_TRANSACTION_TYPE");
        if (txTypeOverride != null && !txTypeOverride.isBlank()) txType = txTypeOverride.trim();

        String partyB = businessShortcode;
        String partyBOverride = System.getenv("DARAJA_PARTYB");
        if (partyBOverride != null && !partyBOverride.isBlank()) partyB = partyBOverride.trim();

        String accountRef = "BOOK-" + booking.getId();
        String desc = "Nganya Experience - " + event.getTitle();

        String token = darajaService.getAccessToken();

        Map<String, Object> resp = darajaService.stkPush(
                token,
                phone254,
                amount,
                businessShortcode,
                partyB,
                txType,
                accountRef,
                desc
        );

        // Daraja may return errors without CheckoutRequestID; be defensive.
        String checkoutRequestId = resp.get("CheckoutRequestID") == null ? null : String.valueOf(resp.get("CheckoutRequestID"));
        String merchantRequestId = resp.get("MerchantRequestID") == null ? null : String.valueOf(resp.get("MerchantRequestID"));

        if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
            return ResponseEntity.status(502).body(Map.of(
                    "status", "ERROR",
                    "message", "Daraja did not return CheckoutRequestID",
                    "daraja", resp
            ));
        }

        // Save payment row (one-to-one per booking). If a payment exists already, overwrite it.
        MpesaPayment payment = paymentRepository.findByBooking_Id(booking.getId()).orElse(
                MpesaPayment.builder().booking(booking).build()
        );
        payment.setPhone(phone254);
        payment.setAmount((double) amount);
        payment.setCheckoutRequestId(checkoutRequestId);
        payment.setMerchantRequestId(merchantRequestId);
        payment.setStatus(MpesaPayment.Status.PENDING);
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
                .orElse(null);

        if (payment == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Payment not found", "checkoutRequestId", checkoutRequestId));
        }

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
        // Safaricom sends: { Body: { stkCallback: { MerchantRequestID, CheckoutRequestID, ResultCode, ResultDesc, CallbackMetadata }} }
        Map body = (Map) payload.get("Body");
        if (body == null) return ResponseEntity.ok(Map.of("ok", true));
        Map stk = (Map) body.get("stkCallback");
        if (stk == null) return ResponseEntity.ok(Map.of("ok", true));

        String checkoutRequestId = String.valueOf(stk.get("CheckoutRequestID"));
        Integer resultCode = stk.get("ResultCode") == null ? null : Integer.parseInt(String.valueOf(stk.get("ResultCode")));

        MpesaPayment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId).orElse(null);
        if (payment == null) {
            // Unknown checkout id - still return 200
            return ResponseEntity.ok(Map.of("ok", true));
        }

        payment.setRawCallback(objectMapper.writeValueAsString(payload));

        if (resultCode != null && resultCode == 0) {
            String receipt = extractReceipt(stk);
            payment.setMpesaReceipt(receipt);
            payment.setStatus(MpesaPayment.Status.PAID);
            paymentRepository.save(payment);

            // Confirm booking, issue ticket, reduce seat
            bookingService.confirmPaid(payment.getBooking().getId());
        } else {
            payment.setStatus(MpesaPayment.Status.FAILED);
            paymentRepository.save(payment);

            // Mark booking failed (user can retry)
            bookingService.markFailed(payment.getBooking().getId());
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String normalizePhone(String phone) {
        String p = phone == null ? "" : phone.trim();
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
