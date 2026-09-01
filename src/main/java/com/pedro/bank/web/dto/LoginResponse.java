package com.pedro.bank.web.dto;

/**
 * Either a finished login or a demand for the second factor. When
 * {@code requiresFaceVerification} is true the JWT is deliberately absent: the
 * client has to clear the face challenge before it gets one.
 */
public record LoginResponse(String token, String tokenType, long expiresIn, String name,
                            boolean requiresFaceVerification, String challengeToken) {

    public static LoginResponse complete(AuthResponse auth) {
        return new LoginResponse(auth.token(), auth.tokenType(), auth.expiresIn(), auth.name(),
                false, null);
    }

    public static LoginResponse faceRequired(String challengeToken, String name) {
        return new LoginResponse(null, null, 0, name, true, challengeToken);
    }
}
