package com.meridianair.ams.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(
        @NotBlank String mfaChallenge,
        @NotBlank String code
) {}
