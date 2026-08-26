package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationUserIdOrderByCreatedAtAsc(Long conversationUserId);

    List<ChatMessage> findAllByOrderByCreatedAtAsc();
}