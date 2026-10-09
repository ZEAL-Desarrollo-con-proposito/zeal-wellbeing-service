package co.zeal.wellbeing.chat;

import co.zeal.wellbeing.shared.error.ResourceNotFoundException;

/** Conversación 1:1 entre un empleado y un psicólogo: {@code <employeeId>:<psychologistId>}. */
public record ConversationId(String employeeId, String psychologistId) {

    static final String NOT_FOUND = "Esta conversación no existe.";

    public static ConversationId parse(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return new ConversationId(parts[0], parts[1]);
    }

    public boolean includes(String userId) {
        return employeeId.equals(userId) || psychologistId.equals(userId);
    }

    public String value() {
        return employeeId + ":" + psychologistId;
    }
}
