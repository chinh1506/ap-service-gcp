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
            int chunkSize = 30;
            if (lockRanges == null || lockRanges.isEmpty()) return;
            for (int i = 0; i < lockRanges.size(); i += chunkSize) {
                int end = Math.min(lockRanges.size(), i + chunkSize);
                List<TargetRange> chunk = lockRanges.subList(i, end);
                sendApiToProtect(targetSSID, chunk, editors);
            }
        } catch (Exception e) {
            System.err.println("Failed with error: " + e.getMessage());
            throw new IOException(e);
        }
    }

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
        return email.endsWith("@googlegroups.com") || email.contains("-group@");
    }

    public Spreadsheet getSpreadsheetMetadata(String fileId) throws IOException {
        return this.getSheetsService().spreadsheets().get(fileId).execute();
    }


    public void removeAllProtections(Spreadsheet spreadsheet) throws IOException {
        String spreadsheetId = spreadsheet.getSpreadsheetId();
        if (spreadsheet.getSheets() == null) {
            return;
        }

        List<Request> deleteRequests = new ArrayList<>();
        for (Sheet sheet : spreadsheet.getSheets()) {
            List<ProtectedRange> protections = sheet.getProtectedRanges();
            if (protections != null && !protections.isEmpty()) {
                for (ProtectedRange pr : protections) {
                    DeleteProtectedRangeRequest deleteRequest = new DeleteProtectedRangeRequest()
                            .setProtectedRangeId(pr.getProtectedRangeId());

                    deleteRequests.add(new Request().setDeleteProtectedRange(deleteRequest));
                }
            }
        }
        if (!deleteRequests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(deleteRequests);

            getSheetsService().spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

            System.out.println("Deleted " + deleteRequests.size() + " protected ranges in file " + spreadsheetId);
        } else {
            System.out.println("File don't have any protected ranges.");
        }
    }

    public void unhideColumns(String spreadsheetId, int sheetId, int startColumn, int endColumn) throws IOException {

        DimensionRange dimensionRange = new DimensionRange()
                .setSheetId(sheetId)
                .setDimension("COLUMNS")
                .setStartIndex(startColumn)
                .setEndIndex(endColumn);

        DimensionProperties properties = new DimensionProperties()
                .setHiddenByUser(false);

        UpdateDimensionPropertiesRequest updateRequest = new UpdateDimensionPropertiesRequest()
                .setRange(dimensionRange)
                .setProperties(properties)
                .setFields("hiddenByUser");

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setUpdateDimensionProperties(updateRequest)));

        getSheetsService().spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

        System.out.println("Unaided columns from " + startColumn + " to " + (endColumn - 1) + " in sheet " + sheetId);
    }

    public void unhideSpecificColumns(String spreadsheetId, Map<Integer, List<Integer>> sheetIdToColumnIndices) throws IOException {
        if (sheetIdToColumnIndices == null || sheetIdToColumnIndices.isEmpty()) {
            return;
        }
        List<Request> allRequests = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : sheetIdToColumnIndices.entrySet()) {
            int sheetId = entry.getKey();
            List<Integer> cols = entry.getValue();
            if (cols == null || cols.isEmpty()) continue;
            Collections.sort(cols);
            int start = cols.get(0);
            int prev = start;
            for (int i = 1; i < cols.size(); i++) {
                int current = cols.get(i);
                if (current == prev + 1) {
                    prev = current;
                } else {
                    allRequests.add(createUnhideRequest(sheetId, start, prev + 1));
                    start = current;
                    prev = current;
                }
            }
            allRequests.add(createUnhideRequest(sheetId, start, prev + 1));
        }

        if (!allRequests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchBody = new BatchUpdateSpreadsheetRequest()
                    .setRequests(allRequests);
            getSheetsService().spreadsheets().batchUpdate(spreadsheetId, batchBody).execute();
            System.out.println("Sent " + allRequests.size() + " unhide requests for whole file.");
        }
    }

    public void unhideSpecificRanges(String spreadsheetId, Map<Integer, List<Map<String, Integer>>> sheetIdToRanges) throws IOException {
        if (sheetIdToRanges == null || sheetIdToRanges.isEmpty()) {
            return;
        }
        List<Request> allRequests = new ArrayList<>();
        for (Map.Entry<Integer, List<Map<String, Integer>>> entry : sheetIdToRanges.entrySet()) {

            Integer sheetId = entry.getKey();
            List<Map<String, Integer>> ranges = entry.getValue();

            if (ranges == null || ranges.isEmpty()) continue;

            for (Map<String, Integer> rangeMap : ranges) {
                if (!rangeMap.containsKey("start") || !rangeMap.containsKey("end")) {
                    continue;
                }
                Integer start = rangeMap.get("start");
                Integer end = rangeMap.get("end");

                allRequests.add(createUnhideRequest(sheetId, start, end));
            }
        }
        if (!allRequests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchBody = new BatchUpdateSpreadsheetRequest()
                    .setRequests(allRequests);

            getSheetsService().spreadsheets().batchUpdate(spreadsheetId, batchBody).execute();

            System.out.println("Sent unhide requests for " + allRequests.size() + " ranges.");
        }
    }

    private Request createUnhideRequest(int sheetId, int startIndex, int endIndex) {
        DimensionRange dimensionRange = new DimensionRange()
                .setSheetId(sheetId)
                .setDimension("COLUMNS")
                .setStartIndex(startIndex)
                .setEndIndex(endIndex); // Exclusive
        DimensionProperties properties = new DimensionProperties()
                .setHiddenByUser(false); // Unhide
        UpdateDimensionPropertiesRequest updateRequest = new UpdateDimensionPropertiesRequest()
                .setRange(dimensionRange)
                .setProperties(properties)
                .setFields("hiddenByUser");
        return new Request().setUpdateDimensionProperties(updateRequest);
    }


}
