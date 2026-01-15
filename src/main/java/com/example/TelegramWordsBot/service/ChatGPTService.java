package com.example.TelegramWordsBot.service;

import com.example.TelegramWordsBot.dto.WordData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatGPTService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ChatGPTService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    public List<WordData> processWords(String wordsList) {
        String prompt = 
            "Для каждого английского слова из следующего списка создай JSON объект с полями: original (оригинальное слово), translation (перевод на украинский язык), transcription (транскрипция). " +
            "Верни только валидный JSON массив объектов без дополнительного текста. Список слов: " + wordsList;

        String responseText = chatClient.prompt(prompt).call().content();

        try {
            String cleanedResponse = responseText.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            return objectMapper.readValue(cleanedResponse, new TypeReference<List<WordData>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при парсинге ответа от ChatGPT: " + e.getMessage(), e);
        }
    }
}

