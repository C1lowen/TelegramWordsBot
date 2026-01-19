package com.example.TelegramWordsBot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class ResourceUtils {

    private static final String BASE_FOLDER = "messages/";

    public static String readMessage(String fileName) {
        log.debug("Reading message file: {}", fileName);
        
        try (InputStream is = ResourceUtils.class.getClassLoader().getResourceAsStream(BASE_FOLDER + fileName)) {
            if (is == null) {
                log.error("File not found: {}", BASE_FOLDER + fileName);
                throw new RuntimeException("File not found: " + BASE_FOLDER + fileName);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Successfully read file: {}", fileName);
            return content;
        } catch (IOException e) {
            log.error("Error reading file: {}", BASE_FOLDER + fileName, e);
            throw new RuntimeException("Error reading file: " + BASE_FOLDER + fileName, e);
        }
    }
}

