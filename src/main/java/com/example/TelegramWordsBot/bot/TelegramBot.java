package com.example.TelegramWordsBot.bot;

import com.example.TelegramWordsBot.dto.UserState;
import com.example.TelegramWordsBot.model.WordData;
import com.example.TelegramWordsBot.repository.InMemoryUserSessionRepository;
import com.example.TelegramWordsBot.service.ChatGPTService;
import com.example.TelegramWordsBot.service.GoogleSheetsService;
import com.example.TelegramWordsBot.util.ResourceUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
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
                sendGifWithText(chatId, "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif", "start_message.html");
            } else if (messageText.equals("/sheet_id")) {
                memoryUserSession.setState(chatId, UserState.WAITING_FOR_SHEET_ID);
                sendGifWithText(chatId, "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif", "sheet_id_message.html");
            } else if (memoryUserSession.getState(chatId) == UserState.WAITING_FOR_SHEET_ID) {
                setSheetId(chatId, messageText);
            } else {
                processAndSaveWords(chatId, messageText);
            }
        }
    }

    private Message sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
           return execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException();
        }
    }

    private void setSheetId(long chatId, String message) {
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

    private void sendGifWithText(Long chatId, String gifUrl, String fileName) {
        SendAnimation animation = new SendAnimation();
        animation.setChatId(chatId.toString());
        animation.setParseMode("HTML");
        animation.setAnimation(new InputFile(gifUrl));

        String caption = ResourceUtils.readMessage(fileName);
        animation.setCaption(caption);

        try {
            execute(animation);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }


    public void processAndSaveWords(Long chatId, String messageText) {
        try {
            Message loadingMsg = sendMessage(chatId, "Обробляю слова...");
            Integer messageId = loadingMsg.getMessageId();

            List<WordData> wordsData = chatGPTService.processWords(messageText);

            try {
                googleSheetsService.writeWords(chatId, wordsData);
                editMessage(chatId, messageId, "✅ Дані успішно записані в Google Sheets!");
            } catch (Exception e) {
                editMessage(chatId, messageId, "⚠️ Помилка при записі в Google Sheets: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void editMessage(Long chatId, Integer messageId, String newText) throws TelegramApiException {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(newText);
        execute(edit);
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

