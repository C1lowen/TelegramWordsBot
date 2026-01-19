package com.example.TelegramWordsBot.service;

import com.example.TelegramWordsBot.dto.WordData;
import com.example.TelegramWordsBot.exception.ChatGPTProcessingException;
import com.example.TelegramWordsBot.util.ResourceUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
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
        log.debug("Processing words list: {}", wordsList);

        String template = ResourceUtils.readMessage("promt_GPT");
        String prompt = String.format(template, wordsList);

        try {
            String responseText = chatClient.prompt(prompt).call().content();
            log.debug("Received response from ChatGPT");

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

            List<WordData> result = objectMapper.readValue(cleanedResponse, new TypeReference<List<WordData>>() {});
            log.info("Successfully processed {} words", result.size());
            return result;
            
        } catch (ChatGPTProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing ChatGPT response", e);
            throw new ChatGPTProcessingException("Failed to parse ChatGPT response: " + e.getMessage(), e);
        }
    }
}

