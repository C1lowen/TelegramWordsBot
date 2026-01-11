package com.example.TelegramWordsBot.util;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class ResourceUtils {

    private static final String BASE_FOLDER = "messages/";
    private static final String GIF_FOLDER = "gifs/";

    public static String readMessage(String fileName) {
        try (InputStream is = ResourceUtils.class.getClassLoader().getResourceAsStream(BASE_FOLDER + fileName)) {
            if (is == null) {
                throw new RuntimeException("Файл не найден: " + BASE_FOLDER + fileName);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при чтении файла: " + BASE_FOLDER + fileName, e);
        }
    }
}

