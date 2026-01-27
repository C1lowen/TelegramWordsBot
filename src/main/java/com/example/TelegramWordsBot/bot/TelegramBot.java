package com.example.TelegramWordsBot.bot;

import com.example.TelegramWordsBot.exception.TelegramMessageSendException;
import com.example.TelegramWordsBot.util.ResourceUtils;
import com.example.TelegramWordsBot.util.UserMessageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.bot.username}")
    private String username;

    private final UserMessageProcessor messageProcessor;
    private final TelegramMessageHandler messageHandler;

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            log.debug("Received update without message or text, skipping");
            return;
        }

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        log.debug("Received message from chatId={}: {}", chatId, text);

        boolean accepted = messageProcessor.submit(chatId, () ->
                messageHandler.handle(update, this)
        );

        if (!accepted) {
            log.debug("Message from chatId={} rejected, user is still processing previous message", chatId);
            sendMessage(chatId, "⏳ Зачекай, я ще обробляю попереднє повідомлення");
        }
    }

    // ================= Telegram API =================

    public Message sendMessage(long chatId, String text) {
        log.debug("Sending message to chatId={}", chatId);
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            Message result = execute(message);
            log.debug("Message sent successfully to chatId={}", chatId);
            return result;
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}", chatId, e);
            throw new TelegramMessageSendException(
                    "Failed to send message to chatId=" + chatId, e
            );
        }
    }

    public void sendGifWithText(Long chatId, String gifUrl, String fileName) {
        log.debug("Sending GIF to chatId={} with caption from file: {}", chatId, fileName);
        
        SendAnimation animation = new SendAnimation();
        animation.setChatId(chatId.toString());
        animation.setParseMode("HTML");
        animation.setAnimation(new InputFile(gifUrl));

        String caption = ResourceUtils.readMessage(fileName);
        animation.setCaption(caption);
        try {
            execute(animation);
            log.debug("GIF sent successfully to chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send GIF to chatId={}", chatId, e);
            throw new TelegramMessageSendException(
                    "Failed to send GIF to chatId=" + chatId, e
            );
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


