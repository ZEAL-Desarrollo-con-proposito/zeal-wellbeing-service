package co.zeal.wellbeing.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable page);
}
