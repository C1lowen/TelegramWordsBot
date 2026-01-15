package com.example.TelegramWordsBot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WordData {
    @JsonProperty("original")
    private String original;

    @JsonProperty("translation")
    private String translation;

    @JsonProperty("transcription")
    private String transcription;
}

