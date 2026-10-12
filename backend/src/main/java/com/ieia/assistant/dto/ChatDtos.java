package com.ieia.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {}

    public record ChatRequest(
            UUID conversationId,
            @NotBlank @Size(max = 2000) String message
    ) {}

    public record ChatResponse(
            UUID conversationId,
            String reply,
            Map<String, Object> usedContext,
            List<String> disclaimers,
            String model,
            boolean groundingCheckPassed
    ) {}

    public record ConversationDto(UUID id, String title, Instant createdAt, Instant updatedAt) {}

    public record MessageDto(UUID id, String role, String content, Instant createdAt, String model) {}

    public record ConversationDetail(ConversationDto conversation, List<MessageDto> messages) {}
}
