package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Upload ảnh chung, không cần ID thực thể có sẵn — dùng khi tạo mới sản phẩm/danh mục (chưa lưu, chưa có ID)
 * mà vẫn muốn chọn file ảnh trực tiếp thay vì phải dán link. Trả về URL để gắn vào form trước khi submit.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/image", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()") // allow any logged-in user to upload images (customers + admins)
    public Map<String, String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Map.of("url", fileStorageService.storeGeneralImage(file));
    }
}