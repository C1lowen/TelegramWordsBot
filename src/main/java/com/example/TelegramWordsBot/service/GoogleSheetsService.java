package com.example.TelegramWordsBot.service;

import com.example.TelegramWordsBot.dto.WordData;
import com.example.TelegramWordsBot.exception.CredentialsNotFoundException;
import com.example.TelegramWordsBot.exception.GoogleSheetsException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "Telegram Words Bot";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/spreadsheets");

    private final String credentialsPath;
    private final Sheets sheetsService;

    public GoogleSheetsService(
            @Value("${google.sheets.credentials-path:credentials.json}") String credentialsPath
    ) throws GeneralSecurityException, IOException, CredentialsNotFoundException {
        this.credentialsPath = credentialsPath;
        this.sheetsService = createSheetsService();
    }

    private Sheets createSheetsService() throws IOException, GeneralSecurityException, CredentialsNotFoundException {
        log.debug("Creating Google Sheets service with credentials from: {}", credentialsPath);
        
        final var HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        InputStream credentialsStream = getClass().getClassLoader().getResourceAsStream(credentialsPath);
        if (credentialsStream == null) {
            log.error("Credentials file not found: {}", credentialsPath);
            throw new CredentialsNotFoundException("Credentials file not found: " + credentialsPath);
        }

        GoogleCredentials credentials = ServiceAccountCredentials.fromStream(credentialsStream)
                .createScoped(SCOPES);

        log.info("Google Sheets service created successfully");
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public boolean spreadsheetExists(String spreadsheetId) {
        log.debug("Checking if spreadsheet exists: {}", spreadsheetId);

        if(spreadsheetId.isEmpty()) {
            return false;
        }
        
        try {
            sheetsService.spreadsheets()
                    .get(spreadsheetId)
                    .setIncludeGridData(false)
                    .execute();

            log.debug("Spreadsheet {} exists and is accessible", spreadsheetId);
            return true;

        } catch (GoogleJsonResponseException e) {
            int status = e.getStatusCode();

            if (status == 404 || status == 403) {
                log.warn("Spreadsheet {} not found or access denied (status: {})", spreadsheetId, status);
                return false;
            }

            log.error("Google Sheets API error for spreadsheet {}: HTTP {}", spreadsheetId, status, e);
            throw new GoogleSheetsException("Google Sheets API error: HTTP " + status, e);

        } catch (IOException e) {
            log.error("Connection error while checking spreadsheet {}", spreadsheetId, e);
            throw new GoogleSheetsException("Failed to connect to Google Sheets API", e);
        }
    }

    private String getDefaultSheetName(String sheetId) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets()
                .get(sheetId)
                .setFields("sheets.properties.title")
                .execute();

        return spreadsheet.getSheets()
                .get(0)
                .getProperties()
                .getTitle();
    }


    public void writeWords(List<WordData> words, User user) {
        String sheetId = user.getSheetId();
        log.debug("Writing {} words to spreadsheet {} for user {}", words.size(), sheetId, user.getChatId());

        try {
            String sheetName = getDefaultSheetName(sheetId);
            String range = sheetName + "!A:C";
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(sheetId, range)
                    .execute();

            List<List<Object>> values = response.getValues();
            boolean isEmpty = (values == null || values.isEmpty());
            int nextRow = isEmpty ? 2 : values.size() + 1;

            if (isEmpty) {
                log.debug("Adding headers to empty spreadsheet");
                ValueRange headerBody = new ValueRange()
                        .setValues(List.of(List.of("Original", "Translation", "Transcription")));
                sheetsService.spreadsheets().values()
                        .update(sheetId, sheetName + "!A1:C1", headerBody)
                        .setValueInputOption("RAW")
                        .execute();
            }

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

            log.debug("Words written to range: {}", writeRange);

            colorColumn(sheetId, 0, new float[]{1f, 0.8f, 0.8f}); // Original → светло-розовый
            colorColumn(sheetId, 1, new float[]{0.8f, 1f, 0.8f}); // Translation → светло-зелёный
            colorColumn(sheetId, 2, new float[]{0.8f, 0.8f, 1f}); // Transcription → светло-голубой
            
            log.info("Successfully wrote {} words to spreadsheet {}", words.size(), sheetId);
            
        } catch (IOException e) {
            log.error("Error writing words to spreadsheet {}", sheetId, e);
            throw new GoogleSheetsException("Failed to write words to Google Sheets", e);
        }
    }


    public void colorColumn(String sheetId, int columnIndex, float[] rgbColor) {
        if (rgbColor.length != 3) {
            throw new IllegalArgumentException("rgbColor must contain 3 values: {r, g, b}");
        }
        
        try {

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
        
        } catch (IOException e) {
            log.error("Error coloring column {} in spreadsheet {}", columnIndex, sheetId, e);
            throw new GoogleSheetsException("Failed to color column in Google Sheets", e);
        }
    }
}
