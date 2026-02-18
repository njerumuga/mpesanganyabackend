package com.nganyaexperience.backend.service;

import com.nganyaexperience.backend.entity.Booking;
import com.nganyaexperience.backend.entity.Event;
import com.nganyaexperience.backend.entity.TicketType;
import com.nganyaexperience.backend.repository.BookingRepository;
import com.nganyaexperience.backend.repository.EventRepository;
import com.nganyaexperience.backend.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;

    // Generate a readable ticket code like WRC-001 based on event title and seat sequence.
    // Called ONLY after payment is confirmed.
    public String generateTicketCode(String eventTitle, int seatSeq) {
        if (eventTitle == null || eventTitle.isBlank()) return "NGX-" + String.format("%03d", seatSeq);

        // Build prefix from first letters of words (up to 3 chars)
        String[] parts = eventTitle.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            char c = Character.toUpperCase(p.charAt(0));
            if (Character.isLetterOrDigit(c)) sb.append(c);
            if (sb.length() >= 3) break;
        }
        String prefix = sb.length() > 0 ? sb.toString() : "NGX";
        return prefix + "-" + String.format("%03d", seatSeq);
    }

    @Transactional
    public Booking createBooking(
            String name,
            String phone,
            Long eventId,
            Long ticketTypeId
    ) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        TicketType ticket = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // 🚫 SOLD OUT CHECK (UNCHANGED)
        if (ticket.getSold() >= ticket.getCapacity()) {
            throw new RuntimeException("Ticket SOLD OUT");
        }

        // ✅ CREATE BOOKING AS PENDING (DO NOT REDUCE SEATS YET)
        Booking booking = Booking.builder()
                .customerName(name)
                .phoneNumber(phone)
                .event(event)
                .ticketType(ticket)
                .paymentStatus(Booking.PaymentStatus.PENDING)
                .build();

        return bookingRepository.save(booking);
    }

    /**
     * Mark booking as PAID, increment sold seats, and issue final ticket code.
     * Safe to call multiple times (idempotent).
     */
    @Transactional
    public Booking confirmPaid(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getPaymentStatus() == Booking.PaymentStatus.PAID && booking.getTicketCode() != null) {
            return booking;
        }

        TicketType ticket = ticketTypeRepository.findById(booking.getTicketType().getId())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (ticket.getSold() >= ticket.getCapacity()) {
            throw new RuntimeException("Ticket SOLD OUT");
        }

        // Seat sequence is sold+1
        int seatSeq = ticket.getSold() + 1;
        ticket.setSold(seatSeq);
        ticketTypeRepository.save(ticket);

        booking.setPaymentStatus(Booking.PaymentStatus.PAID);
        booking.setTicketCode(generateTicketCode(booking.getEvent().getTitle(), seatSeq));

        return bookingRepository.save(booking);
    }

    /**
     * Mark a booking as FAILED (payment cancelled/failed).
     * You can still retry by starting a new STK push which will move it back to PENDING.
     */
    @Transactional
    public Booking markFailed(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getPaymentStatus() == Booking.PaymentStatus.PAID) return booking;

        booking.setPaymentStatus(Booking.PaymentStatus.FAILED);
        return bookingRepository.save(booking);
    }

    /**
     * Ensure a booking is in PENDING state so user can retry payment.
     */
    @Transactional
    public Booking ensurePending(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getPaymentStatus() != Booking.PaymentStatus.PAID) {
            booking.setPaymentStatus(Booking.PaymentStatus.PENDING);
            return bookingRepository.save(booking);
        }
        return booking;
    }
}
