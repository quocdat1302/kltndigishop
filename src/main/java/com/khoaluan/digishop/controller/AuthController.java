package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.*;
import com.khoaluan.digishop.service.AuthService;
import com.khoaluan.digishop.service.FacebookTokenVerifierService;
import com.khoaluan.digishop.service.GoogleTokenVerifierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final FacebookTokenVerifierService facebookTokenVerifierService;

    @GetMapping("/providers")
    public AuthProvidersResponse providers() {
        return AuthProvidersResponse.builder()
                .googleEnabled(googleTokenVerifierService.isConfigured())
                .facebookEnabled(facebookTokenVerifierService.isConfigured())
                .build();
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest req) {
        return authService.loginWithGoogle(req);
    }

    @PostMapping("/facebook")
    public AuthResponse facebook(@Valid @RequestBody FacebookLoginRequest req) {
        return authService.loginWithFacebook(req);
    }

    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return authService.verifyOtp(req);
    }

    @PostMapping("/resend-otp")
    public MessageResponse resendOtp(@Valid @RequestBody ResendOtpRequest req) {
        return authService.resendOtp(req);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return authService.forgotPassword(req);
    }

    /** Buoc 1/2 cua quen mat khau: xac thuc OTP, tra ve resetToken tam de dung o /reset-password. */
    @PostMapping("/verify-reset-otp")
    public MessageResponse verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest req) {
        return authService.verifyResetOtp(req);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return authService.resetPassword(req);
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest req) {
        authService.logout(req != null ? req : new LogoutRequest(null));
        return ResponseEntity.noContent().build();
    }
}