package com.example.TelegramWordsBot.service;

import com.example.TelegramWordsBot.model.WordData;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "Telegram Words Bot";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    private final String spreadsheetId;
    private final String sheetName;
    private final String credentialsPath;

    private final Sheets sheetsService;

    // Инжектируем значения через конструктор
    public GoogleSheetsService(
            @Value("${google.sheets.spreadsheet-id}") String spreadsheetId,
            @Value("${google.sheets.sheet-name:Words}") String sheetName,
            @Value("${google.sheets.credentials-path:credentials.json}") String credentialsPath
    ) throws GeneralSecurityException, IOException {
        this.spreadsheetId = spreadsheetId;
        this.sheetName = sheetName;
        this.credentialsPath = credentialsPath;

        this.sheetsService = createSheetsService();
    }

    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        // Загружаем JSON из classpath
        InputStream credentialsStream = getClass().getClassLoader().getResourceAsStream(credentialsPath);
        if (credentialsStream == null) {
            throw new FileNotFoundException("Файл credentials не найден: " + credentialsPath);
        }

        GoogleCredentials credentials = ServiceAccountCredentials.fromStream(credentialsStream)
                .createScoped(SCOPES);

        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Sheets getSheetsService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        
        GoogleCredentials credentials;
        try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
            credentials = ServiceAccountCredentials.fromStream(serviceAccountStream)
                    .createScoped(SCOPES);
        }

        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void writeWords(List<WordData> words) throws IOException {
        // Получаем текущие данные, чтобы найти следующую пустую строку
        String range = sheetName + "!A:C";
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        
        List<List<Object>> values = response.getValues();
        boolean isEmpty = (values == null || values.isEmpty());
        int nextRow = isEmpty ? 2 : values.size() + 1; // Если пусто, начинаем с 2-й строки (1-я для заголовков)

        // Если таблица пустая, добавляем заголовки
        if (isEmpty) {
            ValueRange headerBody = new ValueRange()
                    .setValues(List.of(List.of("Original", "Translation", "Transcription")));
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, sheetName + "!A1:C1", headerBody)
                    .setValueInputOption("RAW")
                    .execute();
        }

        // Подготавливаем данные для записи
        List<List<Object>> data = words.stream()
                .map(word -> Arrays.<Object>asList(
                        word.getOriginal(),
                        word.getTranslation(),
                        word.getTranscription()
                ))
                .toList();

        // Записываем данные
        ValueRange body = new ValueRange()
                .setValues(data);
        
        String writeRange = sheetName + "!A" + nextRow + ":C" + (nextRow + words.size() - 1);
        
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, writeRange, body)
                .setValueInputOption("RAW")
                .execute();
    }
}

