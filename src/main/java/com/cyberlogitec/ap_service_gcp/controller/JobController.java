package com.cyberlogitec.ap_service_gcp.controller;

import com.cyberlogitec.ap_service_gcp.dto.CreateFileExternalRequest;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.model.FolderStructure;
import com.cyberlogitec.ap_service_gcp.service.CloudRunJobService;
import com.cyberlogitec.ap_service_gcp.service.DriveServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.util.GlobalSettingBKG;
import com.cyberlogitec.ap_service_gcp.util.ScriptSettingLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/jobs")
@AllArgsConstructor
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);
    private final CloudRunJobService cloudRunJobService;
    private final ObjectMapper objectMapper;
    private final Sheets sheetsService;
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ScriptSettingLoader scriptSettingLoader;


//    @PostMapping("/exec/{jobName}")
//    public ResponseEntity<?> execute(@PathVariable String jobName, @RequestBody Object payload) throws Exception {
//        JobContext context = new JobContext();
//        context.setTaskId(UUID.randomUUID().toString());
//        context.setPayload(payload);
//        context.setTaskCount(10);
//        log.info("Executing job {}", objectMapper.writeValueAsString(context));
//        this.cloudRunJobService.runCloudRunJob(jobName, context);
//        return ResponseEntity.accepted().build();
//    }

    @PostMapping("/exec/CreateChildFoldersExternal")
    public ResponseEntity<?> createChildFoldersExternal(@RequestBody Object payload) throws Exception {
        JobContext context = new JobContext();
        context.setTaskId(UUID.randomUUID().toString());
        context.setPayload(payload);
        context.setTaskCount(10);
        CreateFileExternalRequest createFileExternalRequest = objectMapper.convertValue(payload, CreateFileExternalRequest.class);

        FolderStructure existingStructure = this.driveServiceHelper.getExistingFolderStructure(createFileExternalRequest.getCopyFolderId());

        GlobalSettingBKG gs = GlobalSettingBKG.builder()
                .scriptSettingsPart1(scriptSettingLoader.getSettingsMap(createFileExternalRequest.getWorkFileId()))
                .scriptSettingsPart2(scriptSettingLoader.getSettingsMap(createFileExternalRequest.getWorkFilePart2Id()))
                .workFileId(createFileExternalRequest.getWorkFileId())
                .build();
        String distList_DataRange = gs.getScriptSettingsPart1().getAsString("control_MakeCopy_DistList_DataRange_External");
        List<List<Object>> fileUnits = this.sheetServiceHelper.inputAPI(createFileExternalRequest.getWorkFileId(), distList_DataRange);
        String coEditorsSheetName = "Distribution List - External";
        int coEditorsSheetLastCol = getSheetColumnCount(createFileExternalRequest.getWorkFileId(), coEditorsSheetName);
        String masterFOEditorDataRange = gs.getScriptSettingsPart1().getAsString("control_MakeCopy_FOEditors_DataRange_External");
        List<List<Object>> fileUnitsAccess = this.sheetServiceHelper.inputAPI(createFileExternalRequest.getWorkFileId(), masterFOEditorDataRange, coEditorsSheetLastCol);
        List<List<Object>> fileUnitContractData = this.sheetServiceHelper.inputAPI(createFileExternalRequest.getWorkFileId(), gs.getScriptSettingsPart1().getAsString("control_MakeCopy_FileUnitContract_DataRange_External"));
        List<List<Object>> apBookingData = this.sheetServiceHelper.inputAPI(createFileExternalRequest.getFileToShareId(), gs.getScriptSettingsPart2().getAsString("fileToShare_ApBookingDataRange"));

        createFileExternalRequest.setFolderStructure(existingStructure);
        createFileExternalRequest.setGs(gs);
        createFileExternalRequest.setFileUnits(fileUnits);
        createFileExternalRequest.setFileUnitsAccess(fileUnitsAccess);
        createFileExternalRequest.setFileUnitContractData(fileUnitContractData);
        createFileExternalRequest.setApBookingData(apBookingData);


        context.setPayload(createFileExternalRequest);
//        System.out.println(objectMapper.writeValueAsString(context));
        this.cloudRunJobService.runCloudRunJob("CreateChildFoldersExternal", context);

        return ResponseEntity.accepted().build();
    }


//    @PostMapping("/api/job-complete")
//    public void jobComplete(@RequestBody CloudEvent event) throws Exception {
//        if (!event.getType().equals("google.cloud.pubsub.topic.v1.messagePublished")) {
//            return;
//        }
//
//        try {
//            Map<String, Object> cloudEventData = this.objectMapper.readValue(event.getData().toBytes(), Map.class);
//            Map<String, Object> pubSubMessage = (Map<String, Object>) cloudEventData.get("message");
//            String base64Data = (String) pubSubMessage.get("data");
//            String decodedLog = new String(Base64.getDecoder().decode(base64Data));
//
//            // 4. Parse Log để lấy thông tin Job
//            Map<String, Object> logEntry = this.objectMapper.readValue(decodedLog, Map.class);
//            Map<String, Object> resource = (Map<String, Object>) logEntry.get("resource");
//            Map<String, String> labels = (Map<String, String>) resource.get("labels");
//
//            String jobName = labels.get("job_name");
//            String location = labels.get("location");
//
//            System.out.println("NHẬN TÍN HIỆU TỪ LOG: Job " + jobName + " đã hoàn thành tại " + location);
//
//            // ---> KÍCH HOẠT LOGIC GHI SHEETS TẠI ĐÂY <---
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private int getSheetColumnCount(String spreadsheetId, String sheetName) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
                .setRanges(Collections.singletonList("'" + sheetName + "'"))
                .setFields("sheets(properties(gridProperties(columnCount)))")
                .execute();
        if (spreadsheet.getSheets().isEmpty()) return 0;
        return spreadsheet.getSheets().get(0).getProperties().getGridProperties().getColumnCount();
    }
}
