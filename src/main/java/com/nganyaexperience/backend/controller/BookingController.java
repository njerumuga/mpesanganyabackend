package com.nganyaexperience.backend.controller;

import com.nganyaexperience.backend.entity.Booking;
import com.nganyaexperience.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public Booking createBooking(@RequestBody BookingRequest request) {
        return bookingService.createBooking(
                request.getCustomerName(),
                request.getPhoneNumber(),
                request.getEventId(),
                request.getTicketTypeId()
        );
    }
}
