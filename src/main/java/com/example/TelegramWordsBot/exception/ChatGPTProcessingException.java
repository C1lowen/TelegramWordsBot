package com.example.TelegramWordsBot.exception;

/**
 * Exception thrown when there is an error processing words through ChatGPT
 */
public class ChatGPTProcessingException extends RuntimeException {
    
    public ChatGPTProcessingException(String message) {
        super(message);
    }
    
    public ChatGPTProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
