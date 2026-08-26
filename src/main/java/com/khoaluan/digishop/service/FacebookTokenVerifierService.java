package com.khoaluan.digishop.service;

import com.khoaluan.digishop.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies Facebook access tokens and retrieves user information.
 *
 * Requires BOTH app.facebook.app-id and app.facebook.app-secret. The Graph API's /debug_token
 * endpoint requires an authenticating access_token (app_id|app_secret) to authorize the
 * inspection call itself - without it Facebook rejects the request outright with
 * "An access token is required to request this resource." Using the app secret here also lets
 * us verify the inspected token's app_id actually matches ours, so a valid token minted for a
 * completely different Facebook app can't be used to log into this one.
 */
@Slf4j
@Service
public class FacebookTokenVerifierService {

    private final RestTemplate restTemplate;
    private final String appId;
    private final String appSecret;
    private final boolean configured;

    public FacebookTokenVerifierService(
            @Value("${app.facebook.app-id:}") String facebookAppId,
            @Value("${app.facebook.app-secret:}") String facebookAppSecret
    ) {
        this.appId = facebookAppId;
        this.appSecret = facebookAppSecret;
        this.configured = facebookAppId != null && !facebookAppId.isBlank()
                && facebookAppSecret != null && !facebookAppSecret.isBlank();
        this.restTemplate = new RestTemplate();
    }

    public boolean isConfigured() {
        return configured;
    }

    public record FacebookUserInfo(String providerId, String email, String name, String pictureUrl) {
    }

    public FacebookUserInfo verify(String accessToken) {
        if (!configured) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FACEBOOK_NOT_CONFIGURED",
                    "Đăng nhập Facebook chưa được cấu hình trên máy chủ.");
        }

        try {
            // App access token dùng để CHÍNH backend này được phép gọi debug_token - không phải
            // token của người dùng. Không có tham số này Facebook từ chối thẳng request.
            String appAccessToken = appId + "|" + appSecret;
            String debugUrl = "https://graph.facebook.com/debug_token?input_token=" + accessToken
                    + "&access_token=" + appAccessToken;
            Map<String, Object> debugResponse = restTemplate.getForObject(debugUrl, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = debugResponse != null ? (Map<String, Object>) debugResponse.get("data") : null;

            if (data == null || !Boolean.TRUE.equals(data.get("is_valid"))) {
                log.warn("Facebook debug_token returned invalid/missing data: {}", debugResponse);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_FACEBOOK_TOKEN",
                        "Token Facebook không hợp lệ hoặc đã hết hạn.");
            }

            // Bắt buộc: token phải được cấp cho ĐÚNG app này, không phải app Facebook nào khác.
            String tokenAppId = String.valueOf(data.get("app_id"));
            if (!appId.equals(tokenAppId)) {
                log.warn("Facebook token app_id mismatch: expected {}, got {}", appId, tokenAppId);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_FACEBOOK_TOKEN",
                        "Token Facebook không được cấp cho ứng dụng này.");
            }

            // Get user info
            String userInfoUrl = "https://graph.facebook.com/me?fields=id,name,email,picture&access_token=" + accessToken;
            Map<String, Object> userInfo = restTemplate.getForObject(userInfoUrl, Map.class);

            if (userInfo == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_FACEBOOK_TOKEN",
                        "Không thể lấy thông tin người dùng từ Facebook.");
            }

            String pictureUrl = null;
            @SuppressWarnings("unchecked")
            Map<String, Object> pictureData = (Map<String, Object>) userInfo.get("picture");
            if (pictureData != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pictureData2 = (Map<String, Object>) pictureData.get("data");
                if (pictureData2 != null) {
                    pictureUrl = (String) pictureData2.get("url");
                }
            }

            return new FacebookUserInfo(
                    (String) userInfo.get("id"),
                    (String) userInfo.get("email"),
                    (String) userInfo.get("name"),
                    pictureUrl
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Facebook token: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_FACEBOOK_TOKEN",
                    "Không thể xác thực token Facebook.");
        }
    }
}