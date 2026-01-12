package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class SheetServiceHelper {
    private final Sheets sheetsService;
    private final GcsService gcsService;

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
            return sheetsService.spreadsheets().values()
                    .batchUpdate(spreadsheetId, requestBody)
                    .execute();
        }, 3);
    }

    public void clearRange(String spreadsheetId, String range) throws IOException {
        // 3. Xóa cũ
        ClearValuesRequest clearRequest = new ClearValuesRequest();
        Utilities.retry(() -> {
            return this.sheetsService.spreadsheets().values().clear(spreadsheetId, range, clearRequest).execute();
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
                        sheetsService.spreadsheets().values()
                                .get(spreadsheetId, inputRange)
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


}
