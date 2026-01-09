package com.example.TelegramWordsBot.repository;

import com.example.TelegramWordsBot.dto.UserState;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserSessionRepository {

    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, String> userSheetIds = new ConcurrentHashMap<>();

    public UserState getState(Long chatId) {
        return userStates.getOrDefault(chatId, UserState.IDLE);
    }

    public void setState(Long chatId, UserState state) {
        userStates.put(chatId, state);
    }

    public void setSheetId(Long chatId, String sheetId) {
        userSheetIds.put(chatId, sheetId);
    }

    public String getSheetId(Long chatId) {
        return userSheetIds.get(chatId);
    }

    public void clear(Long chatId) {
        userStates.remove(chatId);
        userSheetIds.remove(chatId);
    }
}

