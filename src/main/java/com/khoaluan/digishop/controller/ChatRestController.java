package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.ChatMessageDto;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.service.ChatService;
import com.khoaluan.digishop.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * UC-31: lịch sử hội thoại hỗ trợ, tải qua REST lúc mở trang; tin nhắn mới sau đó nhận realtime
 * qua STOMP (xem ChatController + WebSocketConfig).
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;
    private final FileStorageService fileStorageService;

    /** Khách hàng: toàn bộ lịch sử hội thoại của chính mình. */
    @GetMapping("/my-messages")
    public List<ChatMessageDto> getMyMessages(@AuthenticationPrincipal User user) {
        return chatService.getConversation(user.getId());
    }

    /** Admin: danh sách hội thoại (mỗi khách 1 dòng), sắp theo tin nhắn gần nhất. */
    @GetMapping("/admin/conversations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ChatService.ChatConversationSummary> getConversations() {
        return chatService.getConversationSummaries();
    }

    /** Admin: toàn bộ lịch sử hội thoại với một khách hàng cụ thể. */
    @GetMapping("/admin/conversations/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ChatMessageDto> getConversationWithCustomer(@PathVariable Long customerId) {
        return chatService.getConversation(customerId);
    }

    /** Upload ảnh/file cho chat. Trả về URL để gắn vào tin nhắn. */
    @PostMapping(value = "/upload-file", consumes = "multipart/form-data")
    public Map<String, String> uploadChatFile(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file
    ) {
        if (user == null) {
            throw new IllegalArgumentException("Unauthorized");
        }
        String url = fileStorageService.storeChatFile(file);
        return Map.of("url", url);
    }
}