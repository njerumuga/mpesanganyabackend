package com.nganyaexperience.backend.controller;

import com.nganyaexperience.backend.dto.AdminEventRequest;
import com.nganyaexperience.backend.dto.TicketTypeRequest;
import com.nganyaexperience.backend.entity.Event;
import com.nganyaexperience.backend.entity.TicketType;
import com.nganyaexperience.backend.repository.BookingRepository;
import com.nganyaexperience.backend.repository.EventRepository;
import com.nganyaexperience.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminEventController {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final CloudinaryService cloudinaryService;

    // ✅ CREATE EVENT
    @PostMapping(consumes = "multipart/form-data")
    public Event createEvent(
            @RequestPart("event") AdminEventRequest eventRequest,
            @RequestPart("tickets") List<TicketTypeRequest> tickets,
            @RequestPart(value = "poster", required = false) MultipartFile poster
    ) {

        String posterUrl = null;
        if (poster != null && !poster.isEmpty()) {
            posterUrl = cloudinaryService.uploadEventPoster(poster);
        }

        Event event = Event.builder()
                .title(eventRequest.getTitle())
                .description(eventRequest.getDescription())
                .location(eventRequest.getLocation())
                .date(LocalDate.parse(eventRequest.getDate()))
                .time(eventRequest.getTime() == null || eventRequest.getTime().isBlank()
                        ? null
                        : LocalTime.parse(eventRequest.getTime()))
                .status(Event.Status.valueOf(eventRequest.getStatus()))
                .paymentMethod(eventRequest.getPaymentMethod() == null || eventRequest.getPaymentMethod().isBlank()
                        ? Event.PaymentMethod.TILL
                        : Event.PaymentMethod.valueOf(eventRequest.getPaymentMethod()))
                .paymentNumber(eventRequest.getPaymentNumber())
                .paybillAccount(eventRequest.getPaybillAccount())
                .posterUrl(posterUrl)
                .tickets(new ArrayList<>())
                .build();

        Event saved = eventRepository.save(event);

        for (TicketTypeRequest t : tickets) {
            TicketType ticket = TicketType.builder()
                    .name(t.getName())
                    .price(t.getPrice())
                    .capacity(t.getCapacity())
                    .event(saved)
                    .build();
            saved.getTickets().add(ticket);
        }

        return eventRepository.save(saved);
    }

    // ✅ DELETE EVENT
    @DeleteMapping("/{id}")
    @Transactional
    public void deleteEvent(@PathVariable Long id) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        event.getTickets()
                .forEach(t -> bookingRepository.deleteAllByTicketTypeId(t.getId()));

        cloudinaryService.deleteImage(event.getPosterUrl());
        eventRepository.delete(event);
    }
}
