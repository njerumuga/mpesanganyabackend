package com.nganyaexperience.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    private LocalDate date;
    private LocalTime time;

    private String location;

    // stores: /events/filename.jpg
    private String posterUrl;

    @Enumerated(EnumType.STRING)
    private Status status;

    // ---------------------------
    // PAYMENT CONFIG PER EVENT
    // ---------------------------
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod = PaymentMethod.TILL;

    /**
     * For TILL: store Till number (Buy Goods).
     * For PAYBILL: store Paybill shortcode (BusinessShortCode).
     */
    private String paymentNumber;

    /**
     * Optional: Paybill account/reference shown to user for manual Paybill payments.
     * (STK Push still uses AccountReference internally.)
     */
    private String paybillAccount;

    @OneToMany(
            mappedBy = "event",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JsonIgnoreProperties("event")
    private List<TicketType> tickets;

    public enum Status {
        UPCOMING,
        PAST
    }

    public enum PaymentMethod {
        TILL,
        PAYBILL
    }
}
