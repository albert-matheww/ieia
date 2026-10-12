package com.ieia.assistant;

import com.ieia.common.BaseIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
public class AiMessage extends BaseIdEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private String role;          // USER | ASSISTANT | SYSTEM

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot")
    private Map<String, Object> contextSnapshot;

    private String model;

    private Integer tokens;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Map<String, Object> getContextSnapshot() { return contextSnapshot; }
    public void setContextSnapshot(Map<String, Object> contextSnapshot) { this.contextSnapshot = contextSnapshot; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getTokens() { return tokens; }
    public void setTokens(Integer tokens) { this.tokens = tokens; }
    public Instant getCreatedAt() { return createdAt; }
}
