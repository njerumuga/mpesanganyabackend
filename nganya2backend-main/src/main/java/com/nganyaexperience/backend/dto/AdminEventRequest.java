package com.nganyaexperience.backend.dto;

import lombok.Data;

@Data
public class AdminEventRequest {
    private String title;
    private String description;
    private String location;
    private String date;
    private String time;
    private String status;

    // Payment (per event)
    // TILL or PAYBILL
    private String paymentMethod;
    // For TILL: Till number, For PAYBILL: Paybill shortcode
    private String paymentNumber;
    // Optional account/reference shown to user (mainly for Paybill manual)
    private String paybillAccount;
}
