package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.ChatMessageDto;
import com.khoaluan.digishop.entity.ChatMessage;
import com.khoaluan.digishop.entity.Role;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.ChatMessageRepository;
import com.khoaluan.digishop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatMessageDto saveCustomerMessage(Long customerId, String content, String fileUrl) {
        User sender = getUserOrThrow(customerId);
        return save(customerId, sender, content, fileUrl);
    }

    @Transactional
    public ChatMessageDto saveAdminMessage(Long adminId, Long targetCustomerId, String content, String fileUrl) {
        User sender = getUserOrThrow(adminId);
        if (sender.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ Admin/Sales Staff mới được trả lời hội thoại hỗ trợ");
        }
        getUserOrThrow(targetCustomerId); // đảm bảo khách hàng tồn tại
        return save(targetCustomerId, sender, content, fileUrl);
    }

    private ChatMessageDto save(Long conversationUserId, User sender, String content, String fileUrl) {
        if ((content == null || content.isBlank()) && (fileUrl == null || fileUrl.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE", "Tin nhắn phải có nội dung hoặc file");
        }
        ChatMessage message = ChatMessage.builder()
                .conversationUserId(conversationUserId)
                .senderId(sender.getId())
                .senderName(sender.getName())
                .senderRole(sender.getRole())
                .content(content != null ? content.trim() : "")
                .fileUrl(fileUrl)
                .createdAt(Instant.now())
                .build();
        return toDto(chatMessageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getConversation(Long conversationUserId) {
        return chatMessageRepository.findByConversationUserIdOrderByCreatedAtAsc(conversationUserId)
                .stream().map(this::toDto).toList();
    }

    /** UC-31 (Admin): danh sách các hội thoại, mỗi khách 1 dòng, kèm tin nhắn gần nhất — dùng cho hộp thư hỗ trợ. */
    @Transactional(readOnly = true)
    public List<ChatConversationSummary> getConversationSummaries() {
        List<ChatMessage> all = chatMessageRepository.findAllByOrderByCreatedAtAsc();
        Map<Long, ChatMessage> lastByCustomer = new LinkedHashMap<>();
        for (ChatMessage m : all) {
            lastByCustomer.put(m.getConversationUserId(), m); // ghi đè dần -> cuối cùng là tin mới nhất
        }

        return lastByCustomer.entrySet().stream()
                .map(e -> {
                    User customer = userRepository.findById(e.getKey()).orElse(null);
                    ChatMessage last = e.getValue();
                    return new ChatConversationSummary(
                            e.getKey(),
                            customer != null ? customer.getName() : ("Khách #" + e.getKey()),
                            customer != null ? customer.getEmail() : null,
                            last.getContent(),
                            last.getSenderRole().name(),
                            last.getCreatedAt()
                    );
                })
                .sorted(Comparator.comparing(ChatConversationSummary::lastMessageAt).reversed())
                .toList();
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(
                m.getId(), m.getConversationUserId(), m.getSenderId(), m.getSenderName(),
                m.getSenderRole().name(), m.getContent(), m.getFileUrl(), m.getCreatedAt()
        );
    }

    public record ChatConversationSummary(
            Long customerId,
            String customerName,
            String customerEmail,
            String lastMessage,
            String lastSenderRole,
            Instant lastMessageAt
    ) {
    }
}