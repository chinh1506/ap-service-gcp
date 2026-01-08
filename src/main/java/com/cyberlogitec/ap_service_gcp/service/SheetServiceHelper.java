package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
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

    /**
     * Write data to sheet by BatchUpdate
     *
     * @param outputRange   A1 range (ex: "Sheet1!A1:B2")
     * @param outputValues  matrix array
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


    public List<List<Object>> inputAPI(String spreadsheetId, String inputRange) throws IOException {
        // Gọi hàm chính với dataLength = -1 (hoặc 0) để báo hiệu không cần padding
        return inputAPI(spreadsheetId, inputRange, -1);
    }

    /**
     * Đọc dữ liệu từ Sheet và chuẩn hóa độ dài hàng (Padding).
     *
     * @param spreadsheetId ID của trang tính.
     * @param inputRange    Dải ô cần đọc (VD: "Sheet1!A1:C10").
     * @param dataLength    Độ dài mong muốn của mỗi hàng (số cột).
     * @return Danh sách các hàng đã được chuẩn hóa.
     */
    public List<List<Object>> inputAPI(String spreadsheetId, String inputRange, int dataLength) throws IOException {

        // 1. Gọi API lấy dữ liệu (có Retry)
        ValueRange response = Utilities.retry(() ->
                        sheetsService.spreadsheets().values()
                                .get(spreadsheetId, inputRange)
                                .execute()
                , 3);

        List<List<Object>> values = response.getValues();

        // 2. Kiểm tra dữ liệu
        if (values == null) {
            return new ArrayList<>(); // Trả về list rỗng nếu không có dữ liệu
        }

        // 3. Chuẩn hóa độ dài (Padding)
        // Tương đương: map(r => r.length < dataLength ? [...r, ...Array(...)] : r)
        for (List<Object> row : values) {
            // Google Client Library thường trả về ArrayList có thể thay đổi kích thước.
            // Nếu row ngắn hơn dataLength, thêm chuỗi rỗng vào.
            while (row.size() < dataLength) {
                row.add(""); // Thêm ô trống
            }
        }

        return values;
    }


}
