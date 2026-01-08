package com.example.TelegramWordsBot.bot;

import com.example.TelegramWordsBot.model.WordData;
import com.example.TelegramWordsBot.service.ChatGPTService;
import com.example.TelegramWordsBot.service.GoogleSheetsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.bot.username}")
    private String username;

    private final ChatGPTService chatGPTService;
    private final GoogleSheetsService googleSheetsService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TelegramBot(ChatGPTService chatGPTService, GoogleSheetsService googleSheetsService, ObjectMapper objectMapper) {
        this.chatGPTService = chatGPTService;
        this.googleSheetsService = googleSheetsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendMessage(chatId, "Привет! Отправь мне список английских слов, и я помогу создать словарь.");
            } else {
                try {
                    sendMessage(chatId, "Обрабатываю слова...");
                    List<WordData> wordsData = chatGPTService.processWords(messageText);
                    
                    // Записываем данные в Google Sheets
                    try {
                        googleSheetsService.writeWords(wordsData);
                        sendMessage(chatId, "✅ Данные успешно записаны в Google Sheets!");
                    } catch (Exception e) {
                        sendMessage(chatId, "⚠️ Ошибка при записи в Google Sheets: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    // Отправляем результат пользователю
//                    StringBuilder response = new StringBuilder("Результат обработки:\n\n");
//                    for (WordData word : wordsData) {
//                        response.append("Слово: ").append(word.getOriginal()).append("\n");
//                        response.append("Перевод: ").append(word.getTranslation()).append("\n");
//                        response.append("Транскрипция: ").append(word.getTranscription()).append("\n\n");
//                    }
//
//                    String jsonResponse = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wordsData);
//                    sendMessage(chatId, response.toString());
//                    sendMessage(chatId, "JSON:\n" + jsonResponse);
                } catch (Exception e) {
                    sendMessage(chatId, "Ошибка при обработке слов: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }
}

