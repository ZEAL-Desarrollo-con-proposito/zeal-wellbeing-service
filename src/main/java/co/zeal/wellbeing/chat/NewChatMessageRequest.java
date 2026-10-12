package co.zeal.wellbeing.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewChatMessageRequest(
        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 2000, message = "El mensaje no puede superar los 2000 caracteres") String text) {
}
