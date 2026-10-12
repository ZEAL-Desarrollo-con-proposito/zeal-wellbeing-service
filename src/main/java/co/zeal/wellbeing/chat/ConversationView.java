package co.zeal.wellbeing.chat;

/** Participantes de una conversación; lo usa el Real-Time Gateway para enrutar avisos privados. */
public record ConversationView(String id, String employeeId, String psychologistId) {

    static ConversationView of(ConversationId conversation) {
        return new ConversationView(conversation.value(), conversation.employeeId(), conversation.psychologistId());
    }
}
