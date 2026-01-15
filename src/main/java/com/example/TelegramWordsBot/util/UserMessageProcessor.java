package com.example.TelegramWordsBot.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class UserMessageProcessor {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Long, Future<?>> userTasks = new ConcurrentHashMap<>();

    /**
     * Попытка отправить задачу на обработку.
     * @param chatId уникальный id пользователя
     * @param task логика обработки
     * @return true если задача принята, false если пользователь ещё в процессе
     */
    public boolean submit(Long chatId, Runnable task) {
        Future<?> existing = userTasks.get(chatId);
        if (existing != null && !existing.isDone()) {
            return false;
        }

        Future<?> future = executor.submit(() -> {
            try {
                task.run();
            } finally {
                userTasks.remove(chatId);
            }
        });

        userTasks.put(chatId, future);
        return true;
    }
}


