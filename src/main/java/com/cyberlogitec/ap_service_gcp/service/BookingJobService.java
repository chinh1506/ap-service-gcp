package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.dto.CreateFileExternalRequest;
import com.cyberlogitec.ap_service_gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap_service_gcp.dto.GroupedDataDTO;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.model.FolderStructure;
import com.cyberlogitec.ap_service_gcp.util.GlobalSettingBKG;
import com.cyberlogitec.ap_service_gcp.util.ScriptSettingLoader;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingJobService {
    private final CloudRunJobService cloudRunJobService;
    private final ObjectMapper objectMapper;
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ScriptSettingLoader scriptSettingLoader;
    private final GcsService gcsService;

    public void prepareToCreateChildFoldersExternal(Object payload) throws IOException {
        CreateFileExternalRequest createFileExternalRequest = objectMapper.convertValue(payload, CreateFileExternalRequest.class);
        FolderStructure existingStructure = this.driveServiceHelper.getExistingFolderStructure(createFileExternalRequest.getCopyFolderId());

        String workFileId = createFileExternalRequest.getWorkFileId();
        String fileToShareId = createFileExternalRequest.getFileToShareId();

        GlobalSettingBKG gs = GlobalSettingBKG.builder()
                .scriptSettingsPart1(scriptSettingLoader.getSettingsMap(workFileId))
                .scriptSettingsPart2(scriptSettingLoader.getSettingsMap(createFileExternalRequest.getWorkFilePart2Id()))
                .workFileId(workFileId)
                .build();

        String distListFileUnitDataRange = gs.getScriptSettingsPart1().getAsString("control_MakeCopy_DistList_DataRange_External");
        String masterFOEditorDataRange = gs.getScriptSettingsPart1().getAsString("control_MakeCopy_FOEditors_DataRange_External");
        String fileUnitContractDataRangeExternal = gs.getScriptSettingsPart1().getAsString("control_MakeCopy_FileUnitContract_DataRange_External");
        String apBookingDataRange = gs.getScriptSettingsPart2().getAsString("fileToShare_ApBookingDataRange");
        List<List<Object>> apBookingData = this.sheetServiceHelper.inputAPI(fileToShareId, apBookingDataRange);
        this.sheetServiceHelper.clearRange(fileToShareId, apBookingDataRange);

        Map<String, List<List<Object>>> workFileDataMap = this.sheetServiceHelper.getMappedBatchData(workFileId, List.of(distListFileUnitDataRange, masterFOEditorDataRange, fileUnitContractDataRangeExternal));
        Utilities.logMemory("After get work file data");
        List<List<Object>> fileUnitContractData = workFileDataMap.get(fileUnitContractDataRangeExternal);
        List<List<Object>> fileUnits = workFileDataMap.get(distListFileUnitDataRange);
        List<List<Object>> fileUnitsAccess = workFileDataMap.get(masterFOEditorDataRange);

        createFileExternalRequest.setFolderStructure(existingStructure);
        createFileExternalRequest.setGs(gs);
        createFileExternalRequest.setFileUnits(fileUnits);
        createFileExternalRequest.setFileUnitsAccess(fileUnitsAccess);
        createFileExternalRequest.setFileUnitContractData(fileUnitContractData);
        createFileExternalRequest.setApBookingData(apBookingData);

        Utilities.logMemory("Before set data to context");
        JobContext context = new JobContext();
        context.setJobId(UUID.randomUUID().toString());
        context.setTaskCount(10);
        context.setPayload(createFileExternalRequest);
        Utilities.logMemory("Before runCloudRunJob");
        this.cloudRunJobService.runCloudRunJob("CreateChildFoldersExternal", context);
        Utilities.logMemory("After runCloudRunJob");
    }


    public void handleBookingCallBackResult(Map<String, Object> pubSubMessage) throws ExecutionException, InterruptedException, IOException {
        System.out.println(this.objectMapper.writeValueAsString(pubSubMessage));

        Map<String, Object> message = (Map<String, Object>) pubSubMessage.get("message");
        String dataBase64 = (String) message.get("data");
        String logJson = new String(Base64.getDecoder().decode(dataBase64));
        JsonNode rootNode = objectMapper.readTree(logJson);

        JsonNode labelsNode = rootNode.path("labels");
        String executionName = labelsNode.path("run.googleapis.com/execution_name").asText();
        JsonNode protoPayloadNode = rootNode.path("protoPayload");
        JsonNode responseNode = protoPayloadNode.path("response");
        JsonNode statusNode = responseNode.path("status");

        int succeeded = statusNode.path("succeededCount").asInt();
        int failed = statusNode.path("failedCount").asInt();
        String completionTime = statusNode.path("completionTime").asText();

        System.out.println("====== JOB AUDIT LOG RECEIVED ======");
        System.out.println("Time: " + completionTime);
        System.out.println("Succeeded: " + succeeded);
        System.out.println("Failed:   " + failed);

        String jobId = this.cloudRunJobService.getJobValue(executionName);
        System.out.println("Job ID: " + jobId);
        System.out.println("Execution Name: " + executionName);
        List<DataToWriteDTO> allDataToWriteDTOs = new ArrayList<>();
        for (int i = 0; i < succeeded + failed; i++) {
            byte[] bytes = this.gcsService.getFile(jobId + i);
            List<DataToWriteDTO> list = this.objectMapper.readValue(bytes, new TypeReference<List<DataToWriteDTO>>() {});
            allDataToWriteDTOs.addAll(list);
            this.gcsService.deleteFile(jobId + i);
        }

        Map<String, List<DataToWriteDTO>> groupedMap = allDataToWriteDTOs.stream().collect(Collectors.groupingBy(DataToWriteDTO::getFileId));
        List<GroupedDataDTO> result = groupedMap.entrySet().stream().map(entry -> {
            String currentFileId = entry.getKey();
            List<DataToWriteDTO> items = entry.getValue();

            List<String> ranges = items.stream()
                    .map(DataToWriteDTO::getRange)
                    .collect(Collectors.toList());

            List<List<List<Object>>> dataList = items.stream()
                    .map(DataToWriteDTO::getData)
                    .collect(Collectors.toList());

            // Tạo đối tượng mới
            return new GroupedDataDTO(currentFileId, ranges, dataList);
        }).toList();

        result.forEach(groupedDataDTO -> {
            try {
                this.sheetServiceHelper.batchOutputAPIRows(groupedDataDTO.getRanges(), groupedDataDTO.getDataList(), groupedDataDTO.getFileId());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        this.gcsService.deleteFile(jobId);


    }
}
