package com.example.TelegramWordsBot.exception;

public class TelegramMessageSendException extends RuntimeException {
    public TelegramMessageSendException(String message, Throwable cause) {
        super(message, cause);
    }
}