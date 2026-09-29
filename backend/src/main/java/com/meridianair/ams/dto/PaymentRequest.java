package com.meridianair.ams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Never logged or persisted beyond this request - see Payment entity's
 * class comment for what actually gets stored (last 4 digits + outcome,
 * nothing else). */
public record PaymentRequest(
        @NotBlank String cardholderName,
        @NotBlank @Pattern(regexp = "[\\d\\s-]{12,23}", message = "must be a valid card number") String cardNumber,
        @NotBlank @Pattern(regexp = "(0[1-9]|1[0-2])", message = "must be 01-12") String expiryMonth,
        @NotBlank @Pattern(regexp = "\\d{2}(\\d{2})?", message = "must be YY or YYYY") String expiryYear,
        @NotBlank @Pattern(regexp = "\\d{3,4}") String cvv
) {}
