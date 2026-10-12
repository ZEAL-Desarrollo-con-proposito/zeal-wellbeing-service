package co.zeal.wellbeing.chat;

import java.time.Instant;

/** Contrato de {@code isChatMessage()} del frontend. */
public record ChatMessageView(String id, String conversationId, String senderId, String senderName, String text,
                              Instant sentAt) {

    static ChatMessageView of(ChatMessage message) {
        return new ChatMessageView(message.getId(), message.getConversationId(), message.getSenderId(),
                message.getSenderName(), message.getText(), message.getSentAt());
    }
}
