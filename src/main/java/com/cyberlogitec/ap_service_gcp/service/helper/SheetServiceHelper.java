package com.cyberlogitec.ap_service_gcp.service.helper;

import com.cyberlogitec.ap_service_gcp.configuration.GoogleClientPool;
import com.cyberlogitec.ap_service_gcp.dto.multitrade.TargetRange;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    public void outputAPI(String outputRange, List<List<Object>> outputValues, String spreadsheetId) throws IOException {

        ValueRange data = new ValueRange()
                .setRange(outputRange)
                .setValues(outputValues);

        BatchUpdateValuesRequest requestBody = new BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setData(Collections.singletonList(data));

        Utilities.retry(() -> {
            return getSheetsService().spreadsheets().values()
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


    public void batchAddProtectionRange(
            String targetSSID,
            List<TargetRange> lockRanges,
            List<String> emails
    ) throws IOException {


        try {
            // 1. Phân loại Email (User vs Group)
            Editors editors = new Editors();
            List<String> userMails = new ArrayList<>();
            List<String> groupMails = new ArrayList<>();

            if (emails != null) {
                for (String email : emails) {
                    if (isEmailGroup(email)) {
                        groupMails.add(email);
                    } else {
                        userMails.add(email);
                    }
                }
            }

            if (!userMails.isEmpty()) editors.setUsers(userMails);
            if (!groupMails.isEmpty()) editors.setGroups(groupMails);

            // 2. Xử lý Chunking (Chia nhỏ mảng nếu > 30 mục)
            int chunkSize = 30;

            // Nếu danh sách rỗng, trả về luôn
            if (lockRanges == null || lockRanges.isEmpty()) return;

            for (int i = 0; i < lockRanges.size(); i += chunkSize) {
                int end = Math.min(lockRanges.size(), i + chunkSize);
                List<TargetRange> chunk = lockRanges.subList(i, end);

                try {
                    sendApiToProtect(targetSSID, chunk, editors);
                    TimeUnit.MILLISECONDS.sleep(1000);

                } catch (GoogleJsonResponseException e) {
                    // Xử lý lỗi "already has sheet protection"
                    if (e.getDetails() != null && e.getDetails().getMessage().contains("already has sheet protection")) {
                        System.err.println("Bỏ qua lỗi: Sheet đã được bảo vệ.");
                        continue; // Bỏ qua và chạy chunk tiếp theo
                    }
                    throw e; // Ném lỗi khác
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Tiến trình bị gián đoạn", e);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed with error: " + e.getMessage());
            throw new IOException(e);
        }
    }

    /**
     * Hàm helper thực hiện gửi Request lên Google API
     */
    private void sendApiToProtect(String targetSSID, List<TargetRange> chunkRanges, Editors editors) throws IOException {

        List<Request> requests = new ArrayList<>();

        for (TargetRange rangeData : chunkRanges) {
            GridRange range = new GridRange()
                    .setSheetId(rangeData.getSheetId());
            if (rangeData.getStartRow() != null) range.setStartRowIndex(rangeData.getStartRow());
            if (rangeData.getEndRow() != null) range.setEndRowIndex(rangeData.getEndRow());
            if (rangeData.getStartCol() != null) range.setStartColumnIndex(rangeData.getStartCol());
            if (rangeData.getEndCol() != null) range.setEndColumnIndex(rangeData.getEndCol());
            ProtectedRange protectedRange = new ProtectedRange()
                    .setRange(range)
                    .setEditors(editors)
                    .setDescription("Protected by Java App"); // Optional: Đặt tên description
            AddProtectedRangeRequest addRequest = new AddProtectedRangeRequest()
                    .setProtectedRange(protectedRange);
            requests.add(new Request().setAddProtectedRange(addRequest));
        }
        BatchUpdateSpreadsheetRequest batchBody = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);

        Utilities.retry(() ->
                        getSheetsService().spreadsheets().batchUpdate(targetSSID, batchBody).execute()
                , 3);
    }

    private boolean isEmailGroup(String email) {
        if (email == null) return false;
        // Logic giả định: Google Groups thường kết thúc bằng googlegroups.com
        // Hoặc bạn có thể tùy chỉnh logic này theo domain công ty bạn.
        return email.endsWith("@googlegroups.com") || email.contains("-group@");
    }

    public Spreadsheet getSpreadsheetMetadata(String fileId) throws IOException {
        return this.getSheetsService().spreadsheets().get(fileId).execute();
    }


    public int removeAllProtections(Spreadsheet spreadsheet) throws IOException {

//        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
//                .setFields("sheets(protectedRanges(protectedRangeId))")
//                .execute();
        String spreadsheetId= spreadsheet.getSpreadsheetId();

        if (spreadsheet.getSheets() == null) {
            return 0;
        }

        List<Request> deleteRequests = new ArrayList<>();

        // Duyệt qua từng Sheet
        for (Sheet sheet : spreadsheet.getSheets()) {
            List<ProtectedRange> protections = sheet.getProtectedRanges();

            // Nếu sheet có protections
            if (protections != null && !protections.isEmpty()) {
                for (ProtectedRange pr : protections) {
                    // Tạo lệnh xóa cho từng Protected Range ID
                    DeleteProtectedRangeRequest deleteRequest = new DeleteProtectedRangeRequest()
                            .setProtectedRangeId(pr.getProtectedRangeId());

                    deleteRequests.add(new Request().setDeleteProtectedRange(deleteRequest));
                }
            }
        }

        // BƯỚC 2: Gửi lệnh Batch Update để xóa
        if (!deleteRequests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(deleteRequests);

            getSheetsService().spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

            System.out.println("Đã xóa " + deleteRequests.size() + " protected ranges trong file " + spreadsheetId);
            return deleteRequests.size();
        } else {
            System.out.println("File không có protected range nào.");
            return 0;
        }
    }


}
