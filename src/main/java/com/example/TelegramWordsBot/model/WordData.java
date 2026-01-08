package com.example.TelegramWordsBot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WordData {
    @JsonProperty("original")
    private String original;

    @JsonProperty("translation")
    private String translation;

    @JsonProperty("transcription")
    private String transcription;

    public WordData() {
    }

    public WordData(String original, String translation, String transcription) {
        this.original = original;
        this.translation = translation;
        this.transcription = transcription;
    }

    public String getOriginal() {
        return original;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getTranscription() {
        return transcription;
    }

    public void setTranscription(String transcription) {
        this.transcription = transcription;
    }
}

