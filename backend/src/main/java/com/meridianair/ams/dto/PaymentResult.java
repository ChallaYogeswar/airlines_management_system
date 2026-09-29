package com.meridianair.ams.dto;

public record PaymentResult(boolean success, String declineReason, BookingSummary booking) {}
