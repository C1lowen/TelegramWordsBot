package com.example.TelegramWordsBot.bot;

import com.example.TelegramWordsBot.dto.UserState;
import com.example.TelegramWordsBot.exception.ChatGPTProcessingException;
import com.example.TelegramWordsBot.exception.GoogleSheetsException;
import com.example.TelegramWordsBot.exception.TelegramMessageSendException;
import com.example.TelegramWordsBot.model.User;
import com.example.TelegramWordsBot.service.ChatGPTService;
import com.example.TelegramWordsBot.service.GoogleSheetsService;
import com.example.TelegramWordsBot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramMessageHandler {

    @Value(value = "${media.gif.start}")
    private String accessRightsGif;
    @Value(value = "${media.gif.sheet-id}")
    private String sheetIdGif;

    private final UserService userService;
    private final GoogleSheetsService googleSheetsService;
    private final ChatGPTService chatGPTService;

    public void handle(Update update, TelegramBot bot) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        User user = userService.findOrCreate(chatId);

        // ---------- AUTH FLOW ----------
        if (user.getUserState() == UserState.WAITING_FOR_AUTH_KEY) {
            log.debug("Processing authorization for chatId={}", chatId);
            boolean success = userService.authorize(chatId, text);

            if (!success) {
                log.warn("Failed authorization attempt for chatId={}", chatId);
                bot.sendMessage(chatId, "❌ Невірний ключ. Спробуйте ще раз");
                return;
            }

            log.info("User {} successfully authorized", chatId);
            bot.sendMessage(chatId, "✅ Авторизація успішна");
            bot.sendGifWithText(
                    chatId,
                    accessRightsGif,
                    "start_message.html"
            );
            return;
        }

        // ---------- /start ----------
        if (text.equals("/start")) {
            log.info("User {} started the bot", chatId);
            userService.setState(chatId, UserState.WAITING_FOR_AUTH_KEY);
            bot.sendMessage(chatId, "🔐 Введіть секретний ключ для доступу");
            return;
        }

        // ---------- BLOCK NON-AUTHORIZED ----------
        if (!userService.isAuthorized(user)) {
            log.debug("Unauthorized access attempt from chatId={}", chatId);
            bot.sendMessage(chatId, "🔒 Спочатку авторизуйтесь через /start");
            return;
        }

        // ---------- SHEET ID ----------
        if (text.equals("/sheet_id")) {
            log.debug("User {} requested sheet ID setup", chatId);
            userService.setState(chatId, UserState.WAITING_FOR_SHEET_ID);
            bot.sendGifWithText(
                    chatId,
                    sheetIdGif,
                    "sheet_id_message.html"
            );
            return;
        }

        if (user.getUserState() == UserState.WAITING_FOR_SHEET_ID) {
            log.debug("Processing sheet ID for chatId={}", chatId);
            if (!googleSheetsService.spreadsheetExists(text)) {
                log.warn("Invalid sheet ID provided by chatId={}: {}", chatId, text);
                bot.sendMessage(chatId,
                        "❌ Ви ввели неправильний ID або не надали доступ до таблиці");
                return;
            }

            user.setSheetId(text);
            user.setUserState(UserState.IDLE);
            userService.updateUser(user);

            log.info("Sheet ID saved for user {}", chatId);
            bot.sendMessage(chatId, "✅ Sheet ID збережено! Тепер надішліть свій список слів");
            return;
        }

        // ---------- DEFAULT ----------
        processAndSaveWords(bot, user, text);
    }

    private void processAndSaveWords(TelegramBot bot, User user, String messageText) {
        Long chatId = user.getChatId();
        log.info("Processing words for user {}, message length: {}", chatId, messageText.length());

        if(user.getSheetId() == null || user.getSheetId().isEmpty()) {
            bot.sendMessage(chatId, "⚠️ Ви не вказали Sheet ID.");
            return;
        }
        
        try {
            bot.sendMessage(chatId, "Обробляю слова...");

            var wordsData = chatGPTService.processWords(messageText);
            log.debug("Processed {} words for user {}", wordsData.size(), chatId);

            googleSheetsService.writeWords(wordsData, user);
            bot.sendMessage(chatId, "✅ Дані успішно записані в Google Sheets!");

        } catch (GoogleSheetsException e) {
            log.error("Google Sheets error while processing words for user {}", chatId, e);
            bot.sendMessage(chatId, "⚠️ Помилка при записі в Google Sheets. Спробуйте ще раз або перевірте Sheet ID.");
            
        } catch (ChatGPTProcessingException e) {
            log.error("ChatGPT processing error for user {}", chatId, e);
            bot.sendMessage(chatId, "⚠️ Помилка при обробці слів через ChatGPT. Перевірте формат списку слів і спробуйте ще раз.");
            
        } catch (TelegramMessageSendException e) {
            log.error("Failed to send message to user {}", chatId, e);
        } catch (Exception e) {
            log.error("Unexpected error while processing words for user {}", chatId, e);
            try {
                bot.sendMessage(chatId, "⚠️ Сталася неочікувана помилка. Спробуйте ще раз.");
            } catch (Exception sendException) {
                log.error("Failed to send error message to user {}", chatId, sendException);
            }
        }
    }
}

