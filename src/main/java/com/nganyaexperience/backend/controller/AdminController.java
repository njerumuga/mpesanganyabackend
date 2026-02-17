package com.nganyaexperience.backend.controller;

import com.nganyaexperience.backend.entity.Booking;
import com.nganyaexperience.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final BookingRepository bookingRepository;

    @GetMapping("/bookings")
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @PutMapping("/bookings/{id}/pay")
    public Booking markAsPaid(@PathVariable Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setPaymentStatus(Booking.PaymentStatus.PAID);
        return bookingRepository.save(booking);
    }
}
