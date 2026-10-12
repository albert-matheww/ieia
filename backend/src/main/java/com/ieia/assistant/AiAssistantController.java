package com.ieia.assistant;

import com.ieia.assistant.dto.ChatDtos;
import com.ieia.common.dto.ApiResponse;
import com.ieia.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AssistantService assistantService;

    public AiAssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ChatDtos.ConversationDto>> conversations() {
        return ApiResponse.ok(assistantService.conversations(SecurityUtils.currentUserId()));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ChatDtos.ConversationDto>> create(
            @RequestBody(required = false) Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                assistantService.createConversation(SecurityUtils.currentUserId(), title)));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<ChatDtos.ConversationDetail> conversation(@PathVariable UUID id) {
        return ApiResponse.ok(assistantService.conversation(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assistantService.deleteConversation(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/chat")
    public ApiResponse<ChatDtos.ChatResponse> chat(@Valid @RequestBody ChatDtos.ChatRequest req) {
        return ApiResponse.ok(assistantService.chat(SecurityUtils.currentUserId(), req));
    }
}
