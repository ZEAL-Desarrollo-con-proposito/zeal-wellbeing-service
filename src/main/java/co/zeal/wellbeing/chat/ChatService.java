package co.zeal.wellbeing.chat;

import io.micrometer.core.instrument.MeterRegistry;

import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.shared.error.ResourceNotFoundException;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HU-09: chat privado. Solo pueden usarlo sus dos participantes y solo si alguna vez tuvieron una
 * cita juntos; a cualquier otra persona se le responde 404.
 */
@Service
public class ChatService {

    static final int HISTORY_LIMIT = 200;

    private final ChatMessageRepository messages;
    private final ReservationRepository reservations;
    private final Clock clock;

    private final MeterRegistry meters;

    public ChatService(ChatMessageRepository messages, ReservationRepository reservations, Clock clock, MeterRegistry meters) {
        this.meters = meters;
        this.messages = messages;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConversationView conversation(AuthenticatedUser user, String conversationId) {
        return ConversationView.of(authorize(user, conversationId));
    }

    /** Del más antiguo al más reciente, limitado a los últimos {@value #HISTORY_LIMIT}. */
    @Transactional(readOnly = true)
    public List<ChatMessageView> history(AuthenticatedUser user, String conversationId) {
        ConversationId conversation = authorize(user, conversationId);
        List<ChatMessageView> latest = new ArrayList<>(messages
                .findByConversationIdOrderBySentAtDesc(conversation.value(), PageRequest.of(0, HISTORY_LIMIT))
                .stream()
                .map(ChatMessageView::of)
                .toList());
        Collections.reverse(latest);
        return latest;
    }

    /** El mensaje queda en el historial antes de que el gateway lo reenvíe a los participantes. */
    @Transactional
    public ChatMessageView send(AuthenticatedUser sender, String conversationId, NewChatMessageRequest request) {
        ConversationId conversation = authorize(sender, conversationId);
        meters.counter("zeal.business.chat.messages", "role", sender.role().name()).increment(); // R2-31, sin contenido
        ChatMessage message = messages.save(ChatMessage.write(conversation, sender.id(), sender.fullName(),
                request.text().trim(), Instant.now(clock)));
        return ChatMessageView.of(message);
    }

    private ConversationId authorize(AuthenticatedUser user, String rawId) {
        ConversationId conversation = ConversationId.parse(rawId);
        boolean allowed = conversation.includes(user.id())
                && reservations.existsByEmployeeIdAndPsychologistId(conversation.employeeId(),
                conversation.psychologistId());
        if (!allowed) {
            throw new ResourceNotFoundException(ConversationId.NOT_FOUND);
        }
        return conversation;
    }
}
