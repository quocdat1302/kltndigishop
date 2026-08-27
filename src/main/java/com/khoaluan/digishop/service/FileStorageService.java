package com.khoaluan.digishop.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.khoaluan.digishop.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Lưu file ảnh lên Cloudinary (cloud storage) thay vì đĩa cục bộ.
 *
 * LÝ DO: Render (và nhiều nền tảng PaaS free-tier khác) dùng ổ đĩa tạm thời (ephemeral disk) -
 * mỗi khi service restart, redeploy, hoặc "ngủ" do không có traffic (free tier tự spin down),
 * toàn bộ file đã lưu trên đĩa cục bộ sẽ bị XÓA SẠCH, dù URL vẫn còn trong database. Cloudinary
 * lưu trữ file trên hạ tầng riêng của họ, tồn tại độc lập với vòng đời server backend.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    private void init() {
        if (cloudName == null || cloudName.isBlank() || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            log.warn("Cloudinary chưa được cấu hình đầy đủ (cloud-name/api-key/api-secret). " +
                    "Upload ảnh sẽ báo lỗi cho tới khi cấu hình đủ 3 biến môi trường CLOUDINARY_*.");
            return;
        }
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    /** @return URL public đầy đủ để lưu vào DB (vd imageUrl của Product), ví dụ "https://res.cloudinary.com/.../products/xxx.jpg" */
    public String storeProductImage(MultipartFile file) {
        return store(file, "digishop/products");
    }

    public String storeCategoryImage(MultipartFile file) {
        return store(file, "digishop/categories");
    }

    /** Dùng khi chưa có ID thực thể (vd đang tạo sản phẩm/danh mục mới, chưa lưu) — chỉ lưu file và trả URL. */
    public String storeGeneralImage(MultipartFile file) {
        return store(file, "digishop/general");
    }

    /** Lưu ảnh/file cho chat hỗ trợ. */
    public String storeChatFile(MultipartFile file) {
        return store(file, "digishop/chat");
    }

    private String store(MultipartFile file, String folder) {
        if (cloudinary == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CLOUDINARY_NOT_CONFIGURED",
                    "Dịch vụ lưu trữ ảnh chưa được cấu hình trên máy chủ.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn file ảnh");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE",
                    "Chỉ chấp nhận ảnh JPEG, PNG, WEBP hoặc GIF");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto"
            ));
            String secureUrl = (String) uploadResult.get("secure_url");
            if (secureUrl == null) {
                throw new IOException("Cloudinary không trả về secure_url");
            }
            return secureUrl;
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Lưu file thất bại, vui lòng thử lại");
        }
    }
}