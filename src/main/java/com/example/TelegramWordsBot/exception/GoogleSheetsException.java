package com.example.TelegramWordsBot.exception;

/**
 * Exception thrown when there is an error working with Google Sheets API
 */
public class GoogleSheetsException extends RuntimeException {
    
    public GoogleSheetsException(String message) {
        super(message);
    }
    
    public GoogleSheetsException(String message, Throwable cause) {
        super(message, cause);
    }
}
