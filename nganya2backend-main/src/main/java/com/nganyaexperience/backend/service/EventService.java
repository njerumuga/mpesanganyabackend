package com.nganyaexperience.backend.service;

import com.nganyaexperience.backend.entity.Event;
import com.nganyaexperience.backend.entity.TicketType;
import com.nganyaexperience.backend.repository.BookingRepository;
import com.nganyaexperience.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public Event createEvent(Event event) {
        if (event.getTickets() != null) {
            event.getTickets().forEach(ticket -> ticket.setEvent(event));
        }
        return eventRepository.save(event);
    }

    public List<Event> getAllEvents() {
        List<Event> events = eventRepository.findAll();

        // ✅ Update 'sold' for each ticket based on bookings
        for (Event event : events) {
            for (TicketType ticket : event.getTickets()) {
                long booked = bookingRepository.countByTicketTypeId(ticket.getId());
                ticket.setSold((int) booked);  // Update sold
            }
        }

        return events;
    }

    public Event getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // ✅ Update 'sold' for each ticket
        for (TicketType ticket : event.getTickets()) {
            long booked = bookingRepository.countByTicketTypeId(ticket.getId());
            ticket.setSold((int) booked);  // Update sold
        }

        return event;
    }
}
