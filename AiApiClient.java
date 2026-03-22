package com.aiplayermod;

import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Клиент для работы с разными AI API.
 * Поддерживает: Gemini, Groq, Grok (xAI), OpenRouter
 */
public class AiApiClient {

    private final AiConfig config;

    public AiApiClient(AiConfig config) {
        this.config = config;
    }

    /**
     * Отправляет запрос к AI и возвращает текст ответа.
     */
    public String ask(String systemPrompt, String userMessage) {
        try {
            String requestBody = buildRequestBody(systemPrompt, userMessage);
            String response = sendHttpRequest(requestBody);
            return parseResponse(response);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка API запроса: " + e.getMessage());
            return null;
        }
    }

    private String buildRequestBody(String systemPrompt, String userMessage) {
        if (config.provider.equalsIgnoreCase("gemini")) {
            return buildGeminiRequest(systemPrompt, userMessage);
        } else {
            return buildOpenAiRequest(systemPrompt, userMessage);
        }
    }

    /**
     * Формат запроса для Google Gemini
     */
    private String buildGeminiRequest(String systemPrompt, String userMessage) {
        JsonObject root = new JsonObject();

        // System instruction
        JsonObject sysInstruction = new JsonObject();
        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", systemPrompt);
        JsonArray sysParts = new JsonArray();
        sysParts.add(sysPart);
        sysInstruction.add("parts", sysParts);
        root.add("system_instruction", sysInstruction);

        // Contents
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonObject userPart = new JsonObject();
        userPart.addProperty("text", userMessage);
        JsonArray parts = new JsonArray();
        parts.add(userPart);
        userContent.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(userContent);
        root.add("contents", contents);

        // Generation config
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", 256);
        genConfig.addProperty("temperature", 0.8f);
        root.add("generationConfig", genConfig);

        return root.toString();
    }

    /**
     * Формат запроса для OpenAI-совместимых API (Groq, Grok, OpenRouter)
     */
    private String buildOpenAiRequest(String systemPrompt, String userMessage) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.model);
        root.addProperty("max_tokens", 256);
        root.addProperty("temperature", 0.8f);

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        root.add("messages", messages);
        return root.toString();
    }

    private String sendHttpRequest(String body) throws IOException {
        URL url = new URL(config.getEndpoint());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);

        if (config.needsBearerAuth()) {
            conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
        }

        // OpenRouter нужен заголовок
        if (config.provider.equalsIgnoreCase("openrouter")) {
            conn.setRequestProperty("HTTP-Referer", "https://minecraft-ai-mod");
            conn.setRequestProperty("X-Title", "Minecraft AI Player");
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        if (status >= 400) {
            AiPlayerMod.LOGGER.error("[AIPlayer] HTTP " + status + ": " + sb.toString());
            return null;
        }

        return sb.toString();
    }

    private String parseResponse(String json) {
        if (json == null) return null;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (config.provider.equalsIgnoreCase("gemini")) {
                // Gemini: candidates[0].content.parts[0].text
                return root.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString().trim();
            } else {
                // OpenAI-compatible: choices[0].message.content
                return root.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString().trim();
            }
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка парсинга ответа: " + e.getMessage() + "\nJSON: " + json);
            return null;
        }
    }
}
