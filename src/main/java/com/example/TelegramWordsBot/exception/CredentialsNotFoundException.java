package com.example.TelegramWordsBot.exception;

/**
 * Exception thrown when credentials file is not found
 */
public class CredentialsNotFoundException extends RuntimeException {
    
    public CredentialsNotFoundException(String message) {
        super(message);
    }
    
    public CredentialsNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
