package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.*;
import com.khoaluan.digishop.entity.*;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.RefreshTokenRepository;
import com.khoaluan.digishop.repository.UserRepository;
import com.khoaluan.digishop.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration REMEMBER_ME_REFRESH_TTL = Duration.ofDays(30);
    private static final Duration DEFAULT_REFRESH_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final FacebookTokenVerifierService facebookTokenVerifierService;
    private final EmailService emailService;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    // ---------- Register / verify ----------

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            Map<String, Object> details = new HashMap<>();
            details.put("confirmPassword", "Mật khẩu xác nhận không khớp");
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Mật khẩu không khớp", details);
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            Map<String, Object> details = new HashMap<>();
            details.put("email", "Email đã được sử dụng");
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email đã tồn tại", details);
        }
        if (req.phone() != null && !req.phone().isBlank() && userRepository.existsByPhone(req.phone())) {
            Map<String, Object> details = new HashMap<>();
            details.put("phone", "Số điện thoại đã được sử dụng");
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN", "Số điện thoại đã tồn tại", details);
        }

        User user = User.builder()
                .name(req.name())
                .email(req.email().toLowerCase())
                .phone(req.phone())
                .passwordHash(passwordEncoder.encode(req.password()))
                .status(UserStatus.PENDING_VERIFICATION)
                .authProvider(AuthProviderType.LOCAL)
                .role(Role.CUSTOMER)
                .identityVerified(false)
                .build();
        user = userRepository.save(user);

        OtpService.IssuedOtp issued = otpService.issue(user.getEmail(), OtpPurpose.REGISTER);
        String devOtpCode = mailEnabled ? null : issued.code();

        return RegisterResponse.builder()
                .userId(user.getId())
                .message(mailEnabled
                        ? "Tài khoản đã được tạo. Vui lòng nhập mã OTP để kích hoạt tài khoản."
                        : "Tài khoản đã được tạo. Mã OTP kích hoạt đang ở chế độ dev.")
                .maskedEmail(maskEmail(user.getEmail()))
                .requiresVerification(true)
                .otpCode(devOtpCode)
                .otpExpiresIn(issued.expiresInSeconds())
                .build();
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest req) {
        otpService.verify(req.email(), req.otp(), req.purpose());

        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản"));

        if (req.purpose() == OtpPurpose.REGISTER && user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
            // Gui email "dang ky thanh cong" ngay sau khi tai khoan duoc kich hoat (khac voi email OTP).
            emailService.sendRegistrationSuccessEmail(user.getEmail(), user.getName());
        }

        return issueAuthResponse(user, true);
    }

    @Transactional
    public MessageResponse resendOtp(ResendOtpRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản"));

        OtpService.IssuedOtp issued = otpService.issue(user.getEmail(), req.purpose());
        Map<String, Object> details = new HashMap<>();
        details.put("cooldownSeconds", 60);
        details.put("otpExpiresIn", issued.expiresInSeconds());
        details.put("maskedEmail", maskEmail(user.getEmail()));
        return MessageResponse.builder()
                .message("Mã OTP mới đã được gửi.")
                .details(details)
                .build();
    }

    // ---------- Login / local ----------

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng"));

        if (user.getAuthProvider() != AuthProviderType.LOCAL || user.getPasswordHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOCIAL_ACCOUNT_ONLY",
                    "Tài khoản này đăng nhập bằng " + user.getAuthProvider() + ", vui lòng dùng nút tương ứng.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_PENDING_VERIFICATION",
                    "Tài khoản cần được xác thực OTP trước khi đăng nhập.");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", "Tài khoản đã bị khóa.");
        }

        return issueAuthResponse(user, req.rememberMe());
    }

    // ---------- Google ----------

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest req) {
        GoogleTokenVerifierService.GoogleUserInfo info = googleTokenVerifierService.verify(req.idToken());

        if (!info.emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GOOGLE_EMAIL_NOT_VERIFIED",
                    "Email Google chưa được xác minh.");
        }

        User user = userRepository.findByAuthProviderAndProviderId(AuthProviderType.GOOGLE, info.providerId())
                .or(() -> userRepository.findByEmailIgnoreCase(info.email()))
                .orElseGet(() -> User.builder()
                        .name(info.name() != null ? info.name() : info.email())
                        .email(info.email().toLowerCase())
                        .authProvider(AuthProviderType.GOOGLE)
                        .providerId(info.providerId())
                        .avatarUrl(info.pictureUrl())
                        .status(UserStatus.ACTIVE)
                        .role(Role.CUSTOMER)
                        .identityVerified(false)
                        .build());

        // First-time login for a pre-existing LOCAL account with the same email: link it to Google.
        if (user.getId() != null && user.getAuthProvider() == AuthProviderType.LOCAL) {
            user.setAuthProvider(AuthProviderType.GOOGLE);
            user.setProviderId(info.providerId());
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }

        user = userRepository.save(user);
        return issueAuthResponse(user, true);
    }

    // ---------- Facebook ----------

    @Transactional
    public AuthResponse loginWithFacebook(FacebookLoginRequest req) {
        FacebookTokenVerifierService.FacebookUserInfo info = facebookTokenVerifierService.verify(req.accessToken());

        if (info.email() == null || info.email().isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FACEBOOK_EMAIL_NOT_PROVIDED",
                    "Facebook không cung cấp email. Vui lòng cấp quyền email cho ứng dụng.");
        }

        User user = userRepository.findByAuthProviderAndProviderId(AuthProviderType.FACEBOOK, info.providerId())
                .or(() -> userRepository.findByEmailIgnoreCase(info.email()))
                .orElseGet(() -> User.builder()
                        .name(info.name() != null ? info.name() : info.email())
                        .email(info.email().toLowerCase())
                        .authProvider(AuthProviderType.FACEBOOK)
                        .providerId(info.providerId())
                        .avatarUrl(info.pictureUrl())
                        .status(UserStatus.ACTIVE)
                        .role(Role.CUSTOMER)
                        .identityVerified(false)
                        .build());

        // First-time login for a pre-existing LOCAL account with the same email: link it to Facebook.
        if (user.getId() != null && user.getAuthProvider() == AuthProviderType.LOCAL) {
            user.setAuthProvider(AuthProviderType.FACEBOOK);
            user.setProviderId(info.providerId());
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }

        user = userRepository.save(user);
        return issueAuthResponse(user, true);
    }

    // ---------- Forgot / reset password ----------

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest req) {
        // Always respond the same way, whether or not the email exists, so we
        // don't leak which addresses have accounts.
        OtpService.IssuedOtp issued = null;
        var localUser = userRepository.findByEmailIgnoreCase(req.email())
                .filter(u -> u.getAuthProvider() == AuthProviderType.LOCAL)
                .orElse(null);

        if (localUser != null) {
            issued = otpService.issue(localUser.getEmail(), OtpPurpose.RESET_PASSWORD);
        }

        if (!mailEnabled && issued != null) {
            Map<String, Object> details = new HashMap<>();
            details.put("otpCode", issued.code());
            details.put("otpExpiresIn", issued.expiresInSeconds());
            return MessageResponse.builder()
                    .message("Nếu email tồn tại trong hệ thống, mã OTP đặt lại mật khẩu đã được tạo cho môi trường dev.")
                    .details(details)
                    .build();
        }

        return MessageResponse.builder()
                .message("Nếu email tồn tại trong hệ thống, mã OTP đặt lại mật khẩu đã được gửi.")
                .build();
    }

    /** Buoc 1/2: xac thuc OTP dat lai mat khau, tra ve resetToken tam (10 phut) de dung o buoc 2. */
    @Transactional
    public MessageResponse verifyResetOtp(VerifyResetOtpRequest req) {
        otpService.verify(req.email(), req.otp(), OtpPurpose.RESET_PASSWORD);

        String resetToken = jwtService.generateResetPasswordToken(req.email().toLowerCase());
        Map<String, Object> details = new HashMap<>();
        details.put("resetToken", resetToken);
        details.put("expiresInSeconds", 600);
        return MessageResponse.builder()
                .message("Xác thực OTP thành công. Vui lòng đặt mật khẩu mới.")
                .details(details)
                .build();
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        String email;
        try {
            email = jwtService.extractResetPasswordEmail(req.resetToken());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID",
                    "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn, vui lòng yêu cầu mã OTP mới.");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản"));

        if (user.getAuthProvider() != AuthProviderType.LOCAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOCIAL_ACCOUNT_ONLY",
                    "Tài khoản social không có mật khẩu local để đặt lại.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        return MessageResponse.builder().message("Mật khẩu đã được cập nhật, hãy đăng nhập lại.").build();
    }

    // ---------- Refresh / logout ----------

    @Transactional
    public RefreshResponse refresh(RefreshRequest req) {
        String hash = sha256(req.refreshToken());
        var stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ"));

        if (stored.isRevoked() || Instant.now().isAfter(stored.getExpiresAt())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh token đã hết hạn, vui lòng đăng nhập lại.");
        }

        // rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        String newRefresh = createRefreshToken(user, DEFAULT_REFRESH_TTL);
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        return RefreshResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefresh)
                .accessTokenExpiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public void logout(LogoutRequest req) {
        if (req.refreshToken() == null || req.refreshToken().isBlank()) return;
        String hash = sha256(req.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    // ---------- Helpers ----------

    private AuthResponse issueAuthResponse(User user, boolean longLivedRefresh) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = createRefreshToken(user, longLivedRefresh ? REMEMBER_ME_REFRESH_TTL : DEFAULT_REFRESH_TTL);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .user(UserDto.from(user))
                .build();
    }

    private String createRefreshToken(User user, Duration ttl) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String opaqueToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = RefreshToken.builder()
                .tokenHash(sha256(opaqueToken))
                .user(user)
                .expiresAt(Instant.now().plus(ttl))
                .revoked(false)
                .build();
        refreshTokenRepository.save(entity);
        return opaqueToken;
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }
}