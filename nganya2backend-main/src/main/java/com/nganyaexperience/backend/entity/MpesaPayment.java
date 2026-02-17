package com.nganyaexperience.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mpesa_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MpesaPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    private String phone;
    private Double amount;

    private String merchantRequestId;
    @Column(unique = true)
    private String checkoutRequestId;

    private String mpesaReceipt;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(length = 5000)
    private String rawCallback;

    public enum Status {
        PENDING,
        PAID,
        FAILED
    }
}
