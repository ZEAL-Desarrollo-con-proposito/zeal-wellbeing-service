package co.zeal.wellbeing.chat;

import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats/{conversationId}")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ConversationView conversation(@CurrentUser AuthenticatedUser user, @PathVariable String conversationId) {
        return chatService.conversation(user, conversationId);
    }

    @GetMapping("/mensajes")
    public List<ChatMessageView> history(@CurrentUser AuthenticatedUser user, @PathVariable String conversationId) {
        return chatService.history(user, conversationId);
    }

    @PostMapping("/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageView send(@CurrentUser AuthenticatedUser user, @PathVariable String conversationId,
                                @Valid @RequestBody NewChatMessageRequest request) {
        return chatService.send(user, conversationId, request);
    }
}
