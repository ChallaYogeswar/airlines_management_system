package com.meridianair.ams.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaCodeRequest(@NotBlank String code) {}
