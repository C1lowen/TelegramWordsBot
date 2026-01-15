package com.example.TelegramWordsBot.bot;

import com.example.TelegramWordsBot.util.ResourceUtils;
import com.example.TelegramWordsBot.util.UserMessageProcessor;
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
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Long chatId = update.getMessage().getChatId();

        boolean accepted = messageProcessor.submit(chatId, () ->
                messageHandler.handle(update, this)
        );

        if (!accepted) {
            sendMessage(chatId, "⏳ Подожди, я ещё обрабатываю прошлое сообщение");
        }
    }

    // ================= Telegram API =================

    public Message sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            return execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException();
        }
    }

    public void sendGifWithText(Long chatId, String gifUrl, String fileName) {
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

    public void editMessage(Long chatId, Integer messageId, String newText) throws TelegramApiException {
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


