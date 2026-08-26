package com.khoaluan.digishop.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.khoaluan.digishop.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies the Google "ID token" a client obtains via Google Identity Services
 * (google.accounts.id.initialize / One Tap / the rendered Sign in with Google button).
 *
 * This never trusts anything the browser says about who the user is - the token's
 * signature, audience and issuer are all checked against Google's public keys.
 */
@Service
public class GoogleTokenVerifierService {

    private final GoogleIdTokenVerifier verifier;
    private final boolean configured;

    public GoogleTokenVerifierService(@Value("${app.google.client-id:}") String googleClientId) {
        this.configured = googleClientId != null && !googleClientId.isBlank();
        this.verifier = configured
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(googleClientId))
                        .build()
                : null;
    }

    public boolean isConfigured() {
        return configured;
    }

    public record GoogleUserInfo(String providerId, String email, boolean emailVerified, String name, String pictureUrl) {
    }

    public GoogleUserInfo verify(String idTokenString) {
        if (!configured) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Đăng nhập Google chưa được cấu hình trên máy chủ.");
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                        "Token Google không hợp lệ hoặc đã hết hạn.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleUserInfo(
                    payload.getSubject(),
                    payload.getEmail(),
                    Boolean.TRUE.equals(payload.getEmailVerified()),
                    (String) payload.get("name"),
                    (String) payload.get("picture")
            );
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                    "Không thể xác thực token Google.");
        }
    }
}
