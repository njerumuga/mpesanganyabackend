package com.nganyaexperience.backend.controller;

import com.nganyaexperience.backend.entity.Event;
import com.nganyaexperience.backend.entity.TicketType;
import com.nganyaexperience.backend.repository.EventRepository;
import com.nganyaexperience.backend.repository.TicketTypeRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tickets")
@CrossOrigin(origins = "*")
public class TicketController {

    private final TicketTypeRepository ticketRepo;
    private final EventRepository eventRepo;

    public TicketController(TicketTypeRepository ticketRepo, EventRepository eventRepo) {
        this.ticketRepo = ticketRepo;
        this.eventRepo = eventRepo;
    }

    @PostMapping("/{eventId}")
    public TicketType addTicket(
            @PathVariable Long eventId,
            @RequestBody TicketType ticket
    ) {
        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        ticket.setEvent(event);
        return ticketRepo.save(ticket);
    }
}
