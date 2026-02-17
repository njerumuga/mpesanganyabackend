package com.nganyaexperience.backend.repository;

import com.nganyaexperience.backend.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 🔥 Count how many bookings exist for a given ticket type
    long countByTicketTypeId(Long ticketTypeId);

    // 🔥 Delete all bookings for a given ticket type
    void deleteAllByTicketTypeId(Long ticketTypeId);
}
