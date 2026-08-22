package com.hammerly.ai.cache;

import com.hammerly.ai.config.HammerlySystemPrompt;
import com.hammerly.ai.dto.ChatMessage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiCacheKeyFactory {
    private static final String KEY_PREFIX = "hammerly:ai:response:v1:";
    private final String systemPromptHash;

    public AiCacheKeyFactory(HammerlySystemPrompt systemPrompt) {
        this.systemPromptHash = sha256(systemPrompt.content());
    }

    public String create(String userId, String conversationId, String prompt,
                         List<ChatMessage> context) {
        MessageDigest digest = newDigest();
        add(digest, "v1");
        add(digest, systemPromptHash);
        add(digest, userId);
        add(digest, conversationId);
        add(digest, normalizePrompt(prompt));
        for (ChatMessage message : context) {
            add(digest, message.role().name());
            add(digest, normalizeContext(message.content()));
        }
        return KEY_PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String normalizePrompt(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private static String normalizeContext(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static String sha256(String value) {
        MessageDigest digest = newDigest();
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
