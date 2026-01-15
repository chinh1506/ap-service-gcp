package com.cyberlogitec.ap_service_gcp.job.implement.bkg;

import com.cyberlogitec.ap_service_gcp.dto.CreateFileExternalRequest;
import com.cyberlogitec.ap_service_gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import com.cyberlogitec.ap_service_gcp.model.FolderStructure;
import com.cyberlogitec.ap_service_gcp.service.DriveServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.GcsService;
import com.cyberlogitec.ap_service_gcp.service.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.util.GlobalSettingBKG;
import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import com.cyberlogitec.ap_service_gcp.util.TaskPartitioner;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("job")
@AllArgsConstructor
public class CreateChildFoldersExternal implements JobPlugin {
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ObjectMapper objectMapper;
    private final GcsService gcsService;


    @Override
    public String getJobName() {
        return "CreateChildFoldersExternal";
    }

    @Override
    public void execute(JobContext context) throws Exception {
        System.out.println("Create ChildFolders External Job");
//        System.out.println(context);
        CreateFileExternalRequest payload = objectMapper.convertValue(context.getPayload(), CreateFileExternalRequest.class);

        String totalTasksStr = System.getenv("CLOUD_RUN_TASK_COUNT");
        String currentTaskIndexStr = System.getenv("CLOUD_RUN_TASK_INDEX");
        System.out.println(currentTaskIndexStr);
        int currentTaskIndex = Integer.parseInt(currentTaskIndexStr);
        int totalTasks = Integer.parseInt(totalTasksStr);

        TaskPartitioner.Partition partition = TaskPartitioner.calculatePartition(payload.getEndRow() - payload.getStartRow() + 1, totalTasks, currentTaskIndex);
        System.out.println(partition);

        if (partition.start < 0 || partition.end < 0) {
            finishJob(getJobName(), context, currentTaskIndex);
            System.exit(0);
        }

        this.createAe2ChildFoldersExternal(payload.getCopyFolderId()
                , partition.start
                , partition.end
                , payload.getGs()
                , payload.getFileToShareId()
                , payload.getFileToShareName()
                , payload.getFolderStructure()
                , payload.getFileUnits()
                , payload.getFileUnitsAccess()
                , payload.getFileUnitContractData()
                , payload.getApBookingData()
                , context.getJobId() + currentTaskIndexStr
        );
        System.exit(0);

    }

    // Class nội bộ để giữ thông tin thư mục (thay thế cho JS Object)
    @Data
    @Builder
    public static class FolderInfo {
        String id;
        String url;

        public FolderInfo(String id, String url) {
            this.id = id;
            this.url = url;
        }
    }

    public void createAe2ChildFoldersExternal(
            String copyFolderId,
            int startRow,
            int endRow,
            GlobalSettingBKG gs, // Giả lập đối tượng settings
            String fileToShareSsID,
            String fileToShareName,
            FolderStructure existingStructure,
            List<List<Object>> fileUnits,
            List<List<Object>> fileUnitsAccess,
            List<List<Object>> fileUnitContractData,
            List<List<Object>> apBookingData,
            String taskId

    ) throws IOException, InterruptedException {
        System.out.println("Chuẩn bị xử lý từ " + startRow + " - " + endRow);

        List<DataToWriteDTO> allResultToWrite = new ArrayList<>();
        Map<String, FolderInfo> folderMap = existingStructure.getFolderMap();
        Map<String, FolderInfo> archiveMap = existingStructure.getArchiveMap();
        String workFileId = gs.getWorkFileId();
        ScriptSetting scriptSettingsPart2 = gs.getScriptSettingsPart2();
        ScriptSetting scriptSettings = gs.getScriptSettingsPart1();
        String apBookingDataRange = scriptSettingsPart2.getAsString("fileToShare_ApBookingDataRange");

        String folderSuffix = "(" + scriptSettings.getAsString("control_MakeCopy_TradeName") + "_Booking Comparison Tool)";

        int email_start_col_index = scriptSettings.getAsInt("control_MakeCopy_EmailStartColIndex");

        for (int i = startRow; i <= endRow; i++) {
            if (i >= fileUnits.size()) break;

            List<Object> currentRow = fileUnits.get(i);
            String name = (!currentRow.isEmpty()) ? String.valueOf(currentRow.get(0)) : "";

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
            System.out.println("Handling: " + name + " order: " + (i + 1));

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
                System.out.println("...The folder have existed: .");
                FolderInfo existingFolder = folderMap.get(folderName);
                countryFolderId = existingFolder.id;
                countryFolderUrl = existingFolder.url;

                FolderInfo archiveFolder = archiveMap.get(archiveFolderName);
                if (archiveFolder != null) {
                    archiveFolderUrl = archiveFolder.url;
                    this.driveServiceHelper.archiveOldFiles(countryFolderId, archiveFolder.id);
                } else {
                    archiveFolderUrl = "ERROR: NOT FOUND";
                    System.out.println("...Cannot find archive folder: " + archiveFolderName);
                }

            } else {
                // Tạo mới
                System.out.println("...Create new folder: .");
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
            System.out.println("..Copying master file");
            Thread.sleep(1000);

            String newFileId = this.driveServiceHelper.copyAndMoveFile(fileToShareSsID, countryFolderId, fileToShareName);
            System.out.println("..Copy master file finished: " + newFileId);
            populateNewSheetData(apBookingData, newFileId, apBookingDataRange, fileUnitContract);
            this.driveServiceHelper.foldersUpdate(workFileId, countryFolderId, emails);
        }

        // Cắt mảng
        int safeEndRow = Math.min(endRow, fileUnits.size());
        if (startRow <= safeEndRow) {
            List<List<Object>> dataToWrite = new ArrayList<>(fileUnits.subList(startRow, safeEndRow + 1));
            String distList_DataRange = scriptSettings.getAsString("control_MakeCopy_DistList_DataRange_External");
            String rangeToWrite = Utilities.calculateSubRangeA1(distList_DataRange, startRow, safeEndRow + 1);
            if (rangeToWrite != null && !dataToWrite.isEmpty()) {
                allResultToWrite.add(new DataToWriteDTO(workFileId, rangeToWrite, dataToWrite));

            }
        }
        this.gcsService.uploadStreaming(taskId, allResultToWrite);
    }

    /**
     * Populate Data
     */
    private void populateNewSheetData(List<List<Object>> apBookingData, String targetFileId, String dataRange, List<String> filterContracts) throws IOException {
        List<List<Object>> filteredData = apBookingData.stream()
                .filter(row -> {
                    if (row.isEmpty()) return false;
                    String col0 = String.valueOf(row.get(0));
                    String col1 = (row.size() > 1) ? String.valueOf(row.get(1)) : "";
                    return !"ZZY_Non-AP".equals(col0) && filterContracts.contains(col1);
                })
                .collect(Collectors.toList());

        this.sheetServiceHelper.outputAPIRows(dataRange, filteredData, targetFileId);
    }


    private void ensureSize(List<Object> list, int size) {
        while (list.size() < size) {
            list.add("");
        }
    }

    public void finishJob(String jobName, JobContext context, int taskIndex) throws JsonProcessingException {
        // 1. Tạo Map chứa dữ liệu muốn gửi
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", jobName); // Dấu hiệu để Sink nhận diện
        logData.put("task_id", context.getJobId());         // <--- CÁI BẠN CẦN
        logData.put("job_name", jobName);
        logData.put("task_index", taskIndex);
        logData.put("status", "SUCCESS");

        // Có thể thêm severity để dễ lọc
        logData.put("severity", "INFO");

        // 2. Chuyển sang JSON String
        ObjectMapper mapper = new ObjectMapper();
        String jsonLog = mapper.writeValueAsString(logData);

        // 3. In ra Console (Cloud Run sẽ bắt cái này)
        System.out.println(jsonLog);


    }
}
