package com.example.TelegramWordsBot.bot;

import com.example.TelegramWordsBot.dto.UserState;
import com.example.TelegramWordsBot.dto.WordData;
import com.example.TelegramWordsBot.model.User;
import com.example.TelegramWordsBot.service.ChatGPTService;
import com.example.TelegramWordsBot.service.GoogleSheetsService;
import com.example.TelegramWordsBot.service.UserService;
import com.example.TelegramWordsBot.util.ResourceUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TelegramMessageHandler {

    private final UserService userService;
    private final GoogleSheetsService googleSheetsService;
    private final ChatGPTService chatGPTService;

    public void handle(Update update, TelegramBot bot) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        User user = userService.findOrCreate(chatId);

        // ---------- /start ----------
        if (text.equals("/start")) {
            userService.setState(chatId, UserState.WAITING_FOR_AUTH_KEY);
            bot.sendMessage(chatId, "🔐 Введите секретный ключ для доступа");
            return;
        }

        // ---------- AUTH FLOW ----------
        if (user.getUserState() == UserState.WAITING_FOR_AUTH_KEY) {
            boolean success = userService.authorize(chatId, text);

            if (!success) {
                bot.sendMessage(chatId, "❌ Неверный ключ. Попробуйте ещё раз");
            }

            bot.sendMessage(chatId, "✅ Авторизация успешна");
            bot.sendGifWithText(
                    chatId,
                    "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif",
                    "start_message.html"
            );
            return;
        }

        // ---------- BLOCK NON-AUTHORIZED ----------
        if (!userService.isAuthorized(user)) {
            bot.sendMessage(chatId, "🔒 Сначала авторизуйтесь через /start");
            return;
        }

        // ---------- SHEET ID ----------
        if (text.equals("/sheet_id")) {
            userService.setState(chatId, UserState.WAITING_FOR_SHEET_ID);
            bot.sendGifWithText(
                    chatId,
                    "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif",
                    "sheet_id_message.html"
            );
            return;
        }

        if (user.getUserState() == UserState.WAITING_FOR_SHEET_ID) {
            if (!googleSheetsService.spreadsheetExists(text)) {
                bot.sendMessage(chatId,
                        "❌ Похоже, это не Sheet ID.\n" +
                                "Пришли только ID, а не всю ссылку.\n\n" +
                                "Пример:\n" +
                                "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms");
                return;
            }

            user.setSheetId(text);
            user.setUserState(UserState.IDLE);
            userService.updateUser(user);

            bot.sendMessage(chatId, "✅ Sheet ID сохранён! Теперь отправьте свой список слов");
            return;
        }

        // ---------- DEFAULT ----------
        processAndSaveWords(bot, user, text);
    }

    private void processAndSaveWords(TelegramBot bot, User user, String messageText) {
        Long chatId = user.getChatId();
        Message loadingMsg = bot.sendMessage(chatId, "Обробляю слова...");
        Integer messageId = loadingMsg.getMessageId();

        try {
            var wordsData = chatGPTService.processWords(messageText);
            googleSheetsService.writeWords(wordsData, user);

            bot.editMessage(chatId, messageId,
                    "✅ Дані успішно записані в Google Sheets!");

        } catch (GoogleSheetsWriteException e) {
            bot.editMessage(chatId, messageId,
                    "⚠️ Помилка при записі в Google Sheets");

        } catch (Exception e) {
            bot.editMessage(chatId, messageId,
                    "❌ Сталася несподівана помилка");
        }
    }
}

