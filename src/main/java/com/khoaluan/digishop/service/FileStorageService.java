package com.khoaluan.digishop.service;

import com.khoaluan.digishop.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Lưu file ảnh trực tiếp lên đĩa cục bộ (thư mục app.upload.dir), không dùng S3/Cloudinary vì
 * không có credentials thật trong môi trường này. Ảnh được phục vụ qua WebConfig ở đường dẫn
 * public "/uploads/**". Muốn chuyển sang cloud storage sau này chỉ cần thay class này.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @Value("${app.upload.dir}")
    private String uploadDir;

    /** @return URL public để lưu vào DB (vd imageUrl của Product), ví dụ "/uploads/products/xxx.jpg" */
    public String storeProductImage(MultipartFile file) {
        return store(file, "products");
    }

    public String storeCategoryImage(MultipartFile file) {
        return store(file, "categories");
    }

    /** Dùng khi chưa có ID thực thể (vd đang tạo sản phẩm/danh mục mới, chưa lưu) — chỉ lưu file và trả URL. */
    public String storeGeneralImage(MultipartFile file) {
        return store(file, "general");
    }

    /** Lưu ảnh/file cho chat hỗ trợ. */
    public String storeChatFile(MultipartFile file) {
        return store(file, "chat");
    }

    private String store(MultipartFile file, String subFolder) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn file ảnh");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE",
                    "Chỉ chấp nhận ảnh JPEG, PNG, WEBP hoặc GIF");
        }

        try {
            Path targetDir = Path.of(uploadDir, subFolder);
            Files.createDirectories(targetDir);

            String extension = extractExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            Path targetPath = targetDir.resolve(filename);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/" + subFolder + "/" + filename;
        } catch (IOException e) {
            log.error("Failed to store uploaded file: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Lưu file thất bại, vui lòng thử lại");
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}