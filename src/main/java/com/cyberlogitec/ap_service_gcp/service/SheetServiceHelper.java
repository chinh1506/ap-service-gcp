package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.configuration.GoogleClientPool;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SheetServiceHelper {
    private final GoogleClientPool googleClientPool;

    private Sheets getSheetsService() throws IOException {
        return googleClientPool.getNextSheetsClient();
    }

    /**
     * Write data to sheet by BatchUpdate
     *
     * @param outputRange   A1 range (ex: "Sheet1!A1:B2")
     * @param outputValues  List of arrays
     * @param spreadsheetId spreadsheet's id
     */
    public void outputAPIRows(String outputRange, List<List<Object>> outputValues, String spreadsheetId) throws IOException {
        ValueRange data = new ValueRange()
                .setRange(outputRange)
                .setMajorDimension("ROWS")
                .setValues(outputValues);

        BatchUpdateValuesRequest requestBody = new BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setData(Collections.singletonList(data));

        Utilities.retry(() -> {
            return this.getSheetsService().spreadsheets().values()
                    .batchUpdate(spreadsheetId, requestBody)
                    .execute();
        }, 3);
    }


    public void batchOutputAPIRows(List<String> outputRanges,
                                   List<List<List<Object>>> outputValues,
                                   String spreadsheetId) throws IOException {

        if (outputRanges.size() != outputValues.size()) {
            throw new IllegalArgumentException("Number of output ranges does not match number of output values");
        }

        List<ValueRange> data = new ArrayList<>();

        for (int i = 0; i < outputRanges.size(); i++) {
            ValueRange vr = new ValueRange()
                    .setRange(outputRanges.get(i))      // range: outputRange
                    .setMajorDimension("ROWS")          // majorDimension: 'ROWS'
                    .setValues(outputValues.get(i));    // values: outputValues
            data.add(vr);
        }

        BatchUpdateValuesRequest body = new BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setData(data);

        Utilities.retry(() -> this.getSheetsService().spreadsheets().values()
                .batchUpdate(spreadsheetId, body)
                .execute(), 3);
    }

    public void clearRange(String spreadsheetId, String range) throws IOException {
        // 3. Xóa cũ
        ClearValuesRequest clearRequest = new ClearValuesRequest();
        Utilities.retry(() -> {
            return this.getSheetsService().spreadsheets().values().clear(spreadsheetId, range, clearRequest).execute();
        }, 3);
    }

    public List<List<Object>> inputAPI(String spreadsheetId, String inputRange) throws IOException {
        return inputAPI(spreadsheetId, inputRange, -1);
    }

    /**
     * Read sheet and normalize the length of rows
     *
     * @param spreadsheetId Spreadsheet's ID
     * @param inputRange    Range to read (Ex: "Sheet1!A1:C10").
     * @param dataLength    the expected length for each row
     * @return list of row was normalized
     */
    public List<List<Object>> inputAPI(String spreadsheetId, String inputRange, int dataLength) throws IOException {
        ValueRange response = Utilities.retry(() ->
                        getSheetsService().spreadsheets().values()
                                .get(spreadsheetId, inputRange)
                                .setValueRenderOption("UNFORMATTED_VALUE")
                                .execute()
                , 3);

        List<List<Object>> values = response.getValues();
        if (values == null) {
            return new ArrayList<>();
        }

        for (List<Object> row : values) {
            while (row.size() < dataLength) {
                row.add("");
            }
        }
        return values;
    }

    /**
     * Retrieves multiple ranges in ONE quota unit and maps them by range name.
     *
     * @param spreadsheetId The ID of your spreadsheet.
     * @param ranges        List of ranges like ["Sheet1!A1:B5", "Sheet2!C10:D15"].
     * @return Map<String, List < List < Object>>> Key is range name, Value is 2D array data.
     */
    public Map<String, List<List<Object>>> getMappedBatchData(String spreadsheetId, List<String> ranges) throws IOException {
        // 1. Gọi API batchGet (Chỉ tốn 1 đơn vị Quota Read)
        BatchGetValuesResponse response = Utilities.retry(() -> getSheetsService().spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(ranges)
                .setValueRenderOption("UNFORMATTED_VALUE") // Giống Apps Script
//                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute(), 3);

        Map<String, List<List<Object>>> dataMap = new HashMap<>();
        List<ValueRange> returnedRanges = response.getValueRanges();

        if (returnedRanges != null) {
            for (int i = 0; i < returnedRanges.size(); i++) {
                String originalKey = ranges.get(i);
                ValueRange rangeObj = returnedRanges.get(i);
                List<List<Object>> values = rangeObj.getValues();
                if (values == null) {
                    values = Collections.emptyList();
                }

                dataMap.put(originalKey, values);
            }
        }

        return dataMap;

    }





}
