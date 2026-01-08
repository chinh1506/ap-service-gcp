package com.cyberlogitec.ap_service_gcp.job.implement.bkg;

import com.cyberlogitec.ap_service_gcp.dto.CreateFileExternalRequest;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import com.cyberlogitec.ap_service_gcp.model.FolderStructure;
import com.cyberlogitec.ap_service_gcp.service.DriveServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("job")
@AllArgsConstructor
public class CreateChildFoldersExternal implements JobPlugin {
    private final Sheets sheetsService;
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ScriptSettingLoader scriptSettingLoader;
    private final ObjectMapper objectMapper;


    @Override
    public String getJobName() {
        return "CreateChildFoldersExternal";
    }

    @Override
    public void execute(JobContext context) throws Exception {
        System.out.println("Create ChildFolders External Job");
        System.out.println(context);
        CreateFileExternalRequest payload = objectMapper.convertValue(context.getPayload(), CreateFileExternalRequest.class);

        String totalTasksStr = System.getenv("CLOUD_RUN_TASK_COUNT");
        String currentTaskIndexStr = System.getenv("CLOUD_RUN_TASK_INDEX");
        int currentTaskIndex = Integer.parseInt(currentTaskIndexStr);
        int totalTasks = Integer.parseInt(totalTasksStr);

        TaskPartitioner.Partition partition = TaskPartitioner.calculatePartition(payload.getEndRow() - payload.getStartRow() + 1, totalTasks, currentTaskIndex);

        if (partition.start < 0 || partition.end > totalTasks || partition.end < 0) {
            System.exit(0);
        }

        this.createAe2ChildFoldersExternal(payload.getCopyFolderId()
                , partition.start
                , partition.end
                , GlobalSettingBKG.builder()
                        .scriptSettingsPart1(scriptSettingLoader.getSettingsMap(payload.getWorkFileId()))
                        .scriptSettingsPart2(scriptSettingLoader.getSettingsMap(payload.getWorkFilePart2Id()))
                        .workFileId(payload.getWorkFileId())
                        .build()
                , payload.getFileToShareId()
                , payload.getFileToShareName());

        System.exit(0);
    }

    // Class nội bộ để giữ thông tin thư mục (thay thế cho JS Object)
    public static class FolderInfo {
        String id;
        String url;

        public FolderInfo(String id, String url) {
            this.id = id;
            this.url = url;
        }
    }

    public List<List<Object>> createAe2ChildFoldersExternal(
            String copyFolderId,
            int startRow,
            int endRow,
            GlobalSettingBKG gs, // Giả lập đối tượng settings
            String fileToShareSsID,
            String fileToShareName
    ) throws IOException {

        System.out.println("Chuẩn bị xử lý từ " + startRow + " - " + endRow);

        // 1. Đọc cấu trúc thư mục hiện có
        FolderStructure existingStructure = this.driveServiceHelper.getExistingFolderStructure(copyFolderId);
        Map<String, FolderInfo> folderMap = existingStructure.getFolderMap();
        Map<String, FolderInfo> archiveMap = existingStructure.getArchiveMap();

        System.out.println("Cấu trúc thư mục hiện có đã được tải.");

        // 2. Tải cài đặt và dữ liệu ban đầu
        String workFileId = gs.getWorkFileId();
        ScriptSetting scriptSettingsPart2 = gs.getScriptSettingsPart2();
        ScriptSetting scriptSettings = gs.getScriptSettingsPart1();

        String coEditorsSheetName = "Distribution List - External";
        String apBookingDataRange = scriptSettingsPart2.getAsString("fileToShare_ApBookingDataRange");

        // Giả lập logic lấy max column
        int coEditorsSheetLastCol = getSheetColumnCount(workFileId, coEditorsSheetName);

        String distList_DataRange = scriptSettings.getAsString("control_MakeCopy_DistList_DataRange_External");
        List<List<Object>> fileUnits = this.sheetServiceHelper.inputAPI(workFileId, distList_DataRange);
        String folderSuffix = "(" + scriptSettings.getAsString("control_MakeCopy_TradeName") + "_Booking Comparison Tool)";
        String masterFOEditorDataRange = scriptSettings.getAsString("control_MakeCopy_FOEditors_DataRange_External");//control_MakeCopy_FileUnitContract_DataRange_External

        List<List<Object>> fileUnitsAccess = this.sheetServiceHelper.inputAPI(workFileId, masterFOEditorDataRange, coEditorsSheetLastCol);
        List<List<Object>> fileUnitContractData = this.sheetServiceHelper.inputAPI(workFileId, scriptSettings.getAsString("control_MakeCopy_FileUnitContract_DataRange_External"));

        int titleRow = scriptSettings.getAsInt("control_MakeCopy_FOSettings_TitleRow");
        String titleRowRangeA1 = "'" + coEditorsSheetName + "'!" + titleRow + ":" + titleRow;

        List<Object> masterCOEditorSheet_cols = new ArrayList<>();
        try {
            ValueRange result = sheetsService.spreadsheets().values().get(workFileId, titleRowRangeA1).execute();
            if (result.getValues() != null && !result.getValues().isEmpty()) {
                masterCOEditorSheet_cols = result.getValues().get(0);
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc hàng tiêu đề: " + e.getMessage());
        }

        int email_start_col_index = masterCOEditorSheet_cols.indexOf(scriptSettings.getAsString("control_MakeCopy_EmailStartColName"));

        for (int i = startRow; i < endRow; i++) {
            if (i >= fileUnits.size()) break;

            List<Object> currentRow = fileUnits.get(i);
            String name = (currentRow.size() > 0) ? String.valueOf(currentRow.get(0)) : "";

            if (name == null || name.isEmpty()) {
                ensureSize(currentRow, 3);
                currentRow.set(1, "");
                currentRow.set(2, "");
                continue;
            }

            if (i >= fileUnitContractData.size() || fileUnitContractData.get(i) == null || fileUnitContractData.get(i).isEmpty()) {
                continue;
            }

            List<String> fileUnitContract = fileUnitContractData.get(i).stream()
                    .map(Object::toString)
                    .filter(item -> item != null && !item.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (fileUnitContract.isEmpty()) continue;

            String folderName = name + " " + folderSuffix;
            String archiveFolderName = name + "_Archived " + folderSuffix;
            System.out.println("Đang xử lý: " + name + " order: " + (i + 1));

            // Lấy email editors
            List<String> emails = new ArrayList<>();
            if (i < fileUnitsAccess.size()) {
                List<Object> accessRow = fileUnitsAccess.get(i);
                if (email_start_col_index >= 0 && email_start_col_index < accessRow.size()) {
                    for (int k = email_start_col_index; k < accessRow.size(); k++) {
                        emails.add(String.valueOf(accessRow.get(k)));
                    }
                }
            }

            String countryFolderId, countryFolderUrl, archiveFolderUrl;

            // 3b. Xử lý logic thư mục
            if (folderMap.containsKey(folderName)) {
                // Đã tồn tại
                System.out.println("...Thư mục đã tồn tại.");
                FolderInfo existingFolder = folderMap.get(folderName);
                countryFolderId = existingFolder.id;
                countryFolderUrl = existingFolder.url;

                FolderInfo archiveFolder = archiveMap.get(archiveFolderName);
                if (archiveFolder != null) {
                    archiveFolderUrl = archiveFolder.url;
                    this.driveServiceHelper.archiveOldFiles(countryFolderId, archiveFolder.id);
                } else {
                    archiveFolderUrl = "ERROR: NOT FOUND";
                    System.out.println("...Không tìm thấy thư mục lưu trữ: " + archiveFolderName);
                }

            } else {
                // Tạo mới
                System.out.println("...Tạo thư mục mới.");
                FolderStructure newStruct = this.driveServiceHelper.createNewFolderStructure(folderName, archiveFolderName, copyFolderId, null, workFileId);

                countryFolderId = newStruct.getFolderMap().get("main").id;
                countryFolderUrl = newStruct.getFolderMap().get("main").url;
                archiveFolderUrl = newStruct.getArchiveMap().get("archive").url;
            }

            // Cập nhật URL vào List bộ nhớ
            ensureSize(currentRow, 3);
            currentRow.set(1, countryFolderUrl);
            currentRow.set(2, archiveFolderUrl);

            // 3c. Sao chép File Master
            System.out.println("..Bắt đầu sao chép tệp master");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String newFileId = this.driveServiceHelper.copyAndMoveFile(fileToShareSsID, countryFolderId, fileToShareName);
            System.out.println("..Sao chép hoàn tất: " + newFileId);

            // 3d. Cập nhật dữ liệu
            populateNewSheetData(fileToShareSsID, newFileId, apBookingDataRange, fileUnitContract);

            // Update editors
            this.driveServiceHelper.foldersUpdate(workFileId, countryFolderId, emails);
        }

        // 4. Ghi lại kết quả
        System.out.println("Hoàn tất xử lý. Ghi lại kết quả URL...");

        // Cắt mảng
        int safeEndRow = Math.min(endRow, fileUnits.size());
        if (startRow < safeEndRow) {
            List<List<Object>> dataToWrite = new ArrayList<>(fileUnits.subList(startRow, safeEndRow));
            String rangeToWrite = Utilities.calculateSubRangeA1(distList_DataRange, startRow, safeEndRow);

            if (rangeToWrite != null && !dataToWrite.isEmpty()) {
                this.sheetServiceHelper.outputAPIRows(rangeToWrite, dataToWrite, workFileId);
            }
        }

        return fileUnits;
    }

    /**
     * Populate Data
     */
    private void populateNewSheetData(String sourceFileId, String targetFileId, String dataRange, List<String> filterContracts) throws IOException {
        // 1. Đọc
        List<List<Object>> apBookingData = this.sheetServiceHelper.inputAPI(sourceFileId, dataRange);

        // 2. Lọc
        List<List<Object>> filteredData = apBookingData.stream()
                .filter(row -> {
                    if (row.isEmpty()) return false;
                    String col0 = String.valueOf(row.get(0));
                    String col1 = (row.size() > 1) ? String.valueOf(row.get(1)) : "";
                    return !"ZZY_Non-AP".equals(col0) && filterContracts.contains(col1);
                })
                .collect(Collectors.toList());

        // 3. Xóa cũ
        ClearValuesRequest clearRequest = new ClearValuesRequest();
        sheetsService.spreadsheets().values().clear(targetFileId, dataRange, clearRequest).execute();

        // 4. Ghi mới
        this.sheetServiceHelper.outputAPIRows(dataRange, filteredData, targetFileId);
    }


    private int getSheetColumnCount(String spreadsheetId, String sheetName) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
                .setRanges(Collections.singletonList("'" + sheetName + "'"))
                .setFields("sheets(properties(gridProperties(columnCount)))")
                .execute();
        if (spreadsheet.getSheets().isEmpty()) return 0;
        return spreadsheet.getSheets().get(0).getProperties().getGridProperties().getColumnCount();
    }

    private void ensureSize(List<Object> list, int size) {
        while (list.size() < size) {
            list.add("");
        }
    }
}
