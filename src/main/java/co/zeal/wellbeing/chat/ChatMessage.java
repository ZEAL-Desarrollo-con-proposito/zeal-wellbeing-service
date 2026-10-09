package co.zeal.wellbeing.chat;

import co.zeal.wellbeing.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Mensaje del chat privado (HU-09). Es contenido clínico: vive solo en el Wellbeing Domain. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    private String id;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(nullable = false)
    private String text;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected ChatMessage() {
        // JPA
    }

    public static ChatMessage write(ConversationId conversation, String senderId, String senderName, String text,
                                    Instant sentAt) {
        ChatMessage message = new ChatMessage();
        message.id = Ids.next("mensaje");
        message.conversationId = conversation.value();
        message.senderId = senderId;
        message.senderName = senderName;
        message.text = text;
        message.sentAt = sentAt;
        return message;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getText() {
        return text;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
