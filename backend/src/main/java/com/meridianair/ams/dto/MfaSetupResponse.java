package com.meridianair.ams.dto;

public record MfaSetupResponse(String secret, String otpAuthUri) {}
