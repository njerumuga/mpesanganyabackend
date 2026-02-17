package com.nganyaexperience.backend.controller;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingRequest {
    private String customerName;
    private String phoneNumber;
    private Long eventId;
    private Long ticketTypeId;
}
