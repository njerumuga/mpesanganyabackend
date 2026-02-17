package com.nganyaexperience.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    @Column(unique = true)
    private String ticketCode;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;
    private String phoneNumber;

    @ManyToOne
    @JoinColumn(name = "event_id")
    @JsonIgnoreProperties({"tickets", "bookings"})
    private Event event;

    @ManyToOne
    @JoinColumn(name = "ticket_type_id")
    @JsonIgnoreProperties({"event", "bookings"})
    private TicketType ticketType;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    public enum PaymentStatus {
        PENDING,
        PAID
    }
}
