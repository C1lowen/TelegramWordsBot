package com.example.TelegramWordsBot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class UserMessageProcessor {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Long, Future<?>> userTasks = new ConcurrentHashMap<>();

    public boolean submit(Long chatId, Runnable task) {
        Future<?> existing = userTasks.get(chatId);
        if (existing != null && !existing.isDone()) {
            return false;
        }

        Future<?> future = executor.submit(() -> {
            try {
                log.debug("Starting task execution for chatId={}", chatId);
                task.run();
                log.debug("Task completed successfully for chatId={}", chatId);
            } catch (Exception e) {
                log.error("Error executing task for chatId={}", chatId, e);
                throw e;
            } finally {
                userTasks.remove(chatId);
                log.debug("Removed task for chatId={}", chatId);
            }
        });

        userTasks.put(chatId, future);
        return true;
    }
}


