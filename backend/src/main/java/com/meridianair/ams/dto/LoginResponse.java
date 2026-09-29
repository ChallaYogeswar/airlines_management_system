package com.meridianair.ams.dto;

/**
 * accessToken/refreshToken are null when requiresMfa is true - the
 * client must call /api/auth/mfa/verify with mfaChallenge + a TOTP code
 * before it receives real tokens.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        UserSummary user,
        boolean requiresMfa,
        String mfaChallenge
) {
    public static LoginResponse issued(String accessToken, String refreshToken, UserSummary user) {
        return new LoginResponse(accessToken, refreshToken, user, false, null);
    }

    public static LoginResponse mfaRequired(String mfaChallenge, UserSummary user) {
        return new LoginResponse(null, null, user, true, mfaChallenge);
    }
}
