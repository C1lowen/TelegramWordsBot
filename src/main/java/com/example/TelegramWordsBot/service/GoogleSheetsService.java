package com.example.TelegramWordsBot.service;

import com.example.TelegramWordsBot.dto.WordData;
import com.example.TelegramWordsBot.model.User;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "Telegram Words Bot";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/spreadsheets");

    private final String sheetName;
    private final String credentialsPath;
    private final Sheets sheetsService;

    public GoogleSheetsService(
            @Value("${google.sheets.sheet-name:Words}") String sheetName,
            @Value("${google.sheets.credentials-path:credentials.json}") String credentialsPath,
            UserService userService) throws GeneralSecurityException, IOException {
        this.sheetName = sheetName;
        this.credentialsPath = credentialsPath;
        this.sheetsService = createSheetsService();
    }

    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        final var HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
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

    public boolean spreadsheetExists(String spreadsheetId) {
        try {
            sheetsService.spreadsheets()
                    .get(spreadsheetId)
                    .setIncludeGridData(false)
                    .execute();

            return true;

        } catch (GoogleJsonResponseException e) {
            int status = e.getStatusCode();

            if (status == 404 || status == 403) {
                return false;
            }

            throw new RuntimeException("Ошибка Google Sheets API", e);

        } catch (IOException e) {
            throw new RuntimeException("Ошибка соединения с Google Sheets", e);
        }
    }

    /**
     * Записывает слова в Google Sheets и добавляет заголовки, если таблица пустая
     */
    public void writeWords(List<WordData> words, User user) throws IOException {
        String sheetId = user.getSheetId();
        String range = sheetName + "!A:C";

        ValueRange response = sheetsService.spreadsheets().values()
                .get(sheetId, range)
                .execute();

        List<List<Object>> values = response.getValues();
        boolean isEmpty = (values == null || values.isEmpty());
        int nextRow = isEmpty ? 2 : values.size() + 1;

        // Если таблица пустая, добавляем заголовки
        if (isEmpty) {
            ValueRange headerBody = new ValueRange()
                    .setValues(List.of(List.of("Original", "Translation", "Transcription")));
            sheetsService.spreadsheets().values()
                    .update(sheetId, sheetName + "!A1:C1", headerBody)
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

        ValueRange body = new ValueRange().setValues(data);
        String writeRange = sheetName + "!A" + nextRow + ":C" + (nextRow + words.size() - 1);

        sheetsService.spreadsheets().values()
                .update(sheetId, writeRange, body)
                .setValueInputOption("RAW")
                .execute();

        colorColumn(sheetId, 0, new float[]{1f, 0.8f, 0.8f}); // Original → светло-розовый
        colorColumn(sheetId, 1, new float[]{0.8f, 1f, 0.8f}); // Translation → светло-зелёный
        colorColumn(sheetId, 2, new float[]{0.8f, 0.8f, 1f}); // Transcription → светло-голубой
    }


    public void colorColumn(String sheetId, int columnIndex, float[] rgbColor) throws IOException {
        if (rgbColor.length != 3) throw new IllegalArgumentException("rgbColor должен содержать 3 значения: {r, g, b}");

        int sheetTabId = 0;

        var color = new Color()
                .setRed(rgbColor[0])
                .setGreen(rgbColor[1])
                .setBlue(rgbColor[2]);

        var cellFormat = new CellFormat().setBackgroundColor(color);

        var range = new GridRange()
                .setSheetId(sheetTabId)
                .setStartColumnIndex(columnIndex)
                .setEndColumnIndex(columnIndex + 1)
                .setStartRowIndex(0)
                .setEndRowIndex(1000);

        var repeatCellRequest = new RepeatCellRequest()
                .setRange(range)
                .setCell(new CellData().setUserEnteredFormat(cellFormat))
                .setFields("userEnteredFormat.backgroundColor");

        var batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(new Request().setRepeatCell(repeatCellRequest)));

        sheetsService.spreadsheets().batchUpdate(sheetId, batchRequest).execute();
    }
}
