package com.example.TelegramWordsBot.bot;

import com.example.TelegramWordsBot.dto.UserState;
import com.example.TelegramWordsBot.model.WordData;
import com.example.TelegramWordsBot.repository.InMemoryUserSessionRepository;
import com.example.TelegramWordsBot.service.ChatGPTService;
import com.example.TelegramWordsBot.service.GoogleSheetsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.bot.username}")
    private String username;

    private static final Pattern SHEET_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-_]{30,100}$");
    private final InMemoryUserSessionRepository memoryUserSession;
    private final ChatGPTService chatGPTService;
    private final GoogleSheetsService googleSheetsService;

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendMessage(chatId, "Привет! Отправь мне список английских слов, и я помогу создать словарь.");
            } else if (messageText.equals("/sheet_id")) {
                memoryUserSession.setState(chatId, UserState.WAITING_FOR_SHEET_ID);
                sendGifWithText(
                        chatId,
                        "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif",
                        """
                        📄 Отправь ID Google Sheets таблицы.
                
                        Как получить ID:
                        1. Открой Google Sheets
                        2. Посмотри на URL:
                           https://docs.google.com/spreadsheets/d/SHEET_ID/edit
                        3. Скопируй часть между /d/ и /edit
                        4. Отправь её сюда следующим сообщением
                        """
                );
            } else if (memoryUserSession.getState(chatId) == UserState.WAITING_FOR_SHEET_ID) {
                setSheetId(messageText, chatId);
            } else {
                try {
                    sendMessage(chatId, "Обрабатываю слова...");
                    List<WordData> wordsData = chatGPTService.processWords(messageText);
                    
                    try {
                        googleSheetsService.writeWords(chatId, wordsData);
                        sendMessage(chatId, "✅ Данные успешно записаны в Google Sheets!");
                    } catch (Exception e) {
                        sendMessage(chatId, "⚠️ Ошибка при записи в Google Sheets: " + e.getMessage());
                        e.printStackTrace();
                    }
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

    private void setSheetId(String message, long chatId) {
        String sheetId = message.trim();

        if (!SHEET_ID_PATTERN.matcher(sheetId).matches()) {
            sendMessage(chatId,
                    "❌ Похоже, это не Sheet ID.\n" +
                            "Пришли только ID, а не всю ссылку.\n\n" +
                            "Пример:\n" +
                            "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms");
            return;
        }

        memoryUserSession.setSheetId(chatId, sheetId);
        memoryUserSession.setState(chatId, UserState.IDLE);

        sendMessage(chatId, "✅ Sheet ID сохранён!");
    }

    private void sendGifWithText(Long chatId, String gifUrl, String text) {
        SendAnimation animation = new SendAnimation();
        animation.setChatId(chatId.toString());
        animation.setAnimation(new InputFile(gifUrl));
        animation.setCaption(text);

        try {
            execute(animation);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
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

