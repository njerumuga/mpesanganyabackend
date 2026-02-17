package com.nganyaexperience.backend.dto;

import lombok.Data;

@Data
public class TicketTypeRequest {
    private String name;
    private Double price;
    private Integer capacity;
}
