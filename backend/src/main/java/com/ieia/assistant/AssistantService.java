package com.ieia.assistant;

import com.ieia.assistant.dto.ChatDtos;
import com.ieia.common.exception.ApiException;
import com.ieia.config.AppProperties;
import com.ieia.integration.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,]*(?:\\.\\d+)?");
    private static final int MAX_HISTORY_TURNS = 8;

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final ConversationContextBuilder contextBuilder;
    private final PromptBuilder promptBuilder;
    private final LlmClient llm;
    private final int maxTokens;

    public AssistantService(AiConversationRepository conversationRepository,
                            AiMessageRepository messageRepository,
                            ConversationContextBuilder contextBuilder,
                            PromptBuilder promptBuilder,
                            LlmClient llm,
                            AppProperties props) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
        this.llm = llm;
        this.maxTokens = props.getLlm().getMaxTokens();
    }

    @Transactional
    public ChatDtos.ChatResponse chat(UUID userId, ChatDtos.ChatRequest req) {
        AiConversation conversation = resolveConversation(userId, req);
        ConversationContextBuilder.Context context = contextBuilder.build(userId);

        List<LlmClient.Turn> history = recentHistory(conversation.getId());

        List<String> disclaimers = new ArrayList<>();
        if (!context.hasData()) {
            disclaimers.add("You have no environmental records yet, so I can only give general guidance.");
        } else {
            disclaimers.add("Figures are from your stored data for "
                    + context.bundle().getOrDefault("dataWindow", "the recorded period") + ".");
            Object months = context.bundle().get("monthsOfHistory");
            if (months instanceof Number n && n.intValue() < 12) {
                disclaimers.add("I only have " + n.intValue() + " month(s) of data, so I can't compare to last year.");
            }
        }

        String reply;
        boolean groundingOk;
        if (!llm.isAvailable()) {
            throw ApiException.llmUnavailable("The AI assistant is not configured (provider "
                    + llm.providerName() + " unavailable). Set IEIA_LLM_PROVIDER / the API key.");
        }
        try {
            LlmClient.Response first = llm.complete(new LlmClient.Request(
                    promptBuilder.system(), history, req.message(), context.json(), maxTokens));
            reply = first.text();
            groundingOk = passesGroundingCheck(reply, context);

            if (!groundingOk) {
                log.info("Assistant reply failed grounding check for user {} — retrying strict", userId);
                LlmClient.Response retry = llm.complete(new LlmClient.Request(
                        promptBuilder.systemStricter(), history, req.message(), context.json(), maxTokens));
                if (passesGroundingCheck(retry.text(), context)) {
                    reply = retry.text();
                    groundingOk = true;
                } else {
                    reply = safeFallback(context);
                    groundingOk = false;
                    disclaimers.add("I kept my answer to figures I could verify against your data.");
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.llmUnavailable("AI assistant call failed: " + e.getMessage());
        }

        persist(conversation, "USER", req.message(), null, null);
        persist(conversation, "ASSISTANT", reply, context.bundle(), llm.providerName());
        conversation.setTitle(deriveTitle(conversation.getTitle(), req.message()));

        return new ChatDtos.ChatResponse(conversation.getId(), reply, context.bundle(),
                disclaimers, llm.providerName(), groundingOk);
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.ConversationDto> conversations(UUID userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(c -> new ChatDtos.ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ChatDtos.ConversationDto createConversation(UUID userId, String title) {
        AiConversation c = new AiConversation();
        c.setUserId(userId);
        if (title != null && !title.isBlank()) c.setTitle(title.trim());
        conversationRepository.save(c);
        return new ChatDtos.ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ChatDtos.ConversationDetail conversation(UUID userId, UUID id) {
        AiConversation c = conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
        List<ChatDtos.MessageDto> messages = messageRepository.findByConversationIdOrderByCreatedAt(id).stream()
                .map(m -> new ChatDtos.MessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt(), m.getModel()))
                .toList();
        return new ChatDtos.ConversationDetail(
                new ChatDtos.ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()), messages);
    }

    @Transactional
    public void deleteConversation(UUID userId, UUID id) {
        AiConversation c = conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));
        messageRepository.deleteByConversationId(id);
        conversationRepository.delete(c);
    }

    // ---- grounding guard -------------------------------------------------

    /** No numeric token in the reply may be absent from the context (± rounding). */
    boolean passesGroundingCheck(String reply, ConversationContextBuilder.Context context) {
        List<Double> allowed = contextBuilder.numericValues(context.bundle());
        Matcher m = NUMBER.matcher(reply == null ? "" : reply);
        while (m.find()) {
            double v;
            try {
                v = Double.parseDouble(m.group().replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (isSafeNumber(v)) continue;
            boolean matched = allowed.stream().anyMatch(a -> closeEnough(v, a));
            if (!matched) {
                log.debug("Grounding check: unverified number {} in reply", v);
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeNumber(double v) {
        // small counts / ordinals, month numbers, plausible calendar years
        if (v == Math.rint(v) && Math.abs(v) <= 12) return true;
        return v >= 1990 && v <= 2100 && v == Math.rint(v);
    }

    private static boolean closeEnough(double a, double b) {
        double tol = Math.max(0.5, Math.abs(b) * 0.02);   // 2% or 0.5 absolute
        return Math.abs(a - b) <= tol
                || Math.abs(a - Math.round(b)) <= 0.5      // reply rounded the value
                || Math.abs(Math.round(a) - b) <= 0.5;
    }

    private String safeFallback(ConversationContextBuilder.Context context) {
        Map<String, Object> b = context.bundle();
        if (!context.hasData()) {
            return "I don't have any environmental records for you yet. Add some electricity, transport, "
                    + "water or waste data and I'll be able to answer questions about your footprint.";
        }
        StringBuilder sb = new StringBuilder("Here is what your recorded data shows");
        if (b.containsKey("period")) sb.append(" for ").append(b.get("period"));
        sb.append(":");
        if (b.get("score") != null) sb.append("\n• Environmental score: ").append(b.get("score")).append("/100");
        if (b.get("co2eKg") != null) sb.append("\n• CO₂e: ").append(b.get("co2eKg")).append(" kg");
        Object tc = b.get("topContributors");
        if (tc instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> top) {
            sb.append("\n• Largest contributor: ").append(top.get("category"))
              .append(" (").append(top.get("pct")).append("%)");
        }
        sb.append("\n\nI kept this answer to figures I can verify against your data.");
        return sb.toString();
    }

    // ---- helpers -----------------------------------------------------

    private AiConversation resolveConversation(UUID userId, ChatDtos.ChatRequest req) {
        if (req.conversationId() != null) {
            return conversationRepository.findByIdAndUserId(req.conversationId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Conversation"));
        }
        AiConversation c = new AiConversation();
        c.setUserId(userId);
        return conversationRepository.save(c);
    }

    private List<LlmClient.Turn> recentHistory(UUID conversationId) {
        List<AiMessage> all = messageRepository.findByConversationIdOrderByCreatedAt(conversationId);
        List<LlmClient.Turn> turns = new ArrayList<>();
        int from = Math.max(0, all.size() - MAX_HISTORY_TURNS);
        for (int i = from; i < all.size(); i++) {
            AiMessage m = all.get(i);
            turns.add(new LlmClient.Turn(m.getRole().toLowerCase(), m.getContent()));
        }
        return turns;
    }

    private void persist(AiConversation conversation, String role, String content,
                         Map<String, Object> contextSnapshot, String model) {
        AiMessage m = new AiMessage();
        m.setConversationId(conversation.getId());
        m.setRole(role);
        m.setContent(content);
        m.setContextSnapshot(contextSnapshot);
        m.setModel(model);
        messageRepository.save(m);
    }

    private static String deriveTitle(String current, String firstMessage) {
        if (current != null && !current.equals("New conversation")) return current;
        String t = firstMessage.strip().replaceAll("\\s+", " ");
        return t.length() <= 60 ? t : t.substring(0, 57) + "...";
    }
}
