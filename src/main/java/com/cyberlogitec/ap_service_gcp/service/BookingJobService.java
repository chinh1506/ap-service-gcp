package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.dto.*;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.dto.FolderStructure;
import com.cyberlogitec.ap_service_gcp.util.GlobalSettingBKG;
import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import com.cyberlogitec.ap_service_gcp.util.ScriptSettingLoader;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
    private final SendGridService sendGridService;
    private final ChartService chartService;

    public void prepareToCreateChildFoldersExternal(Object payload) throws IOException {
        CreateFileExternalDTO createFileExternalDTO = this.objectMapper.convertValue(payload, CreateFileExternalDTO.class);
        FolderStructure existingStructure = this.driveServiceHelper.getExistingFolderStructure(createFileExternalDTO.getToShareFolderId());

        String workFileId = createFileExternalDTO.getWorkFileId();
        String fileToShareId = createFileExternalDTO.getFileToShareId();

        GlobalSettingBKG gs = GlobalSettingBKG.builder()
                .scriptSettingsPart1(scriptSettingLoader.getSettingsMap(workFileId))
                .scriptSettingsPart2(scriptSettingLoader.getSettingsMap(createFileExternalDTO.getWorkFilePart2Id()))
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

        createFileExternalDTO.setFolderStructure(existingStructure);
        createFileExternalDTO.setGs(gs);
        createFileExternalDTO.setFileUnits(fileUnits);
        createFileExternalDTO.setFileUnitsAccess(fileUnitsAccess);
        createFileExternalDTO.setFileUnitContractData(fileUnitContractData);
        createFileExternalDTO.setApBookingData(apBookingData);

        Utilities.logMemory("Before set data to context");
        JobContext context = new JobContext();
        context.setJobId(UUID.randomUUID().toString());
        context.setTaskCount(createFileExternalDTO.getTaskCount());
        context.setPayload(createFileExternalDTO);
        Utilities.logMemory("Before runCloudRunJob");
        this.cloudRunJobService.runJob("CreateChildFoldersExternal", context);
        Utilities.logMemory("After runCloudRunJob");
    }

    public void handleBookingCallBackResult(Map<String, Map<String, Object>> pubSubMessage) throws ExecutionException, InterruptedException, IOException {
//        System.out.println(this.objectMapper.writeValueAsString(pubSubMessage));

        Map<String, Object> message = pubSubMessage.get("message");
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
            List<DataToWriteDTO> list = this.objectMapper.readValue(bytes, new TypeReference<List<DataToWriteDTO>>() {
            });
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
        this.cloudRunJobService.deleteJobCache(executionName);
    }

    // Constants trạng thái
    private static final String STATUS_ERROR_UNDEFINED = "Error";
    private static final String HISTORY_SKIPPED = "Skipped";
    private static final String HISTORY_ERROR = "Error Sending";

    public void notifyToPICButton(String toShareFolderId, String workFileId, boolean isExternal) throws IOException {
        ScriptSetting wfScriptSetting = this.scriptSettingLoader.getSettingsMap(workFileId);
        ScriptSetting fileToShareSettingsMap = null;
        // 1. Initialization
        String fileNameToShare = wfScriptSetting.getAsString("bookingComparison_CreatefileToShare_FileName");
        String salesWeek = wfScriptSetting.getAsString("control_MakeCopy_SalesWeek");
        String fileToShareName = fileNameToShare + "_" + salesWeek;
        String coEditorsSheetName = isExternal ? "Distribution List - External" : "Distribution List";
        String distListRange = isExternal ? wfScriptSetting.getAsString("control_MakeCopy_DistList_DataRange_External") : wfScriptSetting.getAsString("control_MakeCopy_DistList_DataRange");
        String ccEmailRange = isExternal ? wfScriptSetting.getAsString("ae1_NotificationSettings_CClist_External") : wfScriptSetting.getAsString("ae1_NotificationSettings_CClist");
        String bccEmailRange = isExternal ? wfScriptSetting.getAsString("ae1_NotificationSettings_BCClist_External") : wfScriptSetting.getAsString("ae1_NotificationSettings_BCClist");
        String emailContentRange = isExternal ? wfScriptSetting.getAsString("ae1_NotificationSettings_EmailContentRange_External") : wfScriptSetting.getAsString("ae1_NotificationSettings_EmailContentRange");
        String targetWeekRange = wfScriptSetting.getAsString("ae1_NotificationSettings_targetWeek");
        String foEditorRange = isExternal ? wfScriptSetting.getAsString("control_MakeCopy_FOEditors_DataRange_External") : wfScriptSetting.getAsString("control_MakeCopy_FOEditors_DataRange");
        String fileUnitContractRange = wfScriptSetting.getAsString("control_MakeCopy_FileUnitContract_DataRange_External");

        // all data read one time
        Map<String, List<List<Object>>> allDataWorkFile = sheetServiceHelper.getMappedBatchData(workFileId, List.of(fileUnitContractRange, foEditorRange, targetWeekRange, distListRange, ccEmailRange, bccEmailRange, emailContentRange));

        List<List<Object>> fileUnits = allDataWorkFile.get(distListRange);
        String tradeName = wfScriptSetting.getAsString("control_MakeCopy_TradeName");
        String folderSuffix = String.format("(%s_Booking Comparison Tool)", tradeName);
        List<List<Object>> ccEmailListRaw = allDataWorkFile.get(ccEmailRange);
        List<List<Object>> bccEmailListRaw = allDataWorkFile.get(bccEmailRange);
        List<String> ccEmailList = Utilities.flattenList(ccEmailListRaw);
        List<String> bccEmailList = Utilities.flattenList(bccEmailListRaw);
        List<List<Object>> defaultEmailContent = allDataWorkFile.get(emailContentRange);
        List<List<Object>> targetWeekFull = allDataWorkFile.get(targetWeekRange);
        String targetWeek = nameTargetWeek(targetWeekFull);
        List<List<Object>> fileUnitsAccess = allDataWorkFile.get(foEditorRange);
        int emailStartColIndex = Integer.parseInt(wfScriptSetting.getAsString("control_MakeCopy_EmailStartColIndex")) - 1;
        List<List<Object>> fileUnitContractData = isExternal ? allDataWorkFile.get(fileUnitContractRange) : new ArrayList<>();
        System.out.println("Initialization completed");

        // 2. Read Existing Folders
        Map<String, String> checkFolderNames = new HashMap<>();
        String pageToken = null;
        do {
            FileList result = driveServiceHelper.findFolderInSubfolder(toShareFolderId, pageToken);
            for (File file : result.getFiles()) {
                checkFolderNames.put(file.getName(), file.getId());
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);


        List<List<Object>> notificationHistoryRecord = new ArrayList<>();

        // 3. Loop through units
        for (int i = 0; i < fileUnits.size(); i++) {
            String name = (String) fileUnits.get(i).get(0);
            if (name == null || name.isEmpty()) continue;

            if (isExternal) {
                // Check logic for external contract existence
                if (fileUnitContractData.size() <= i || fileUnitContractData.get(i).isEmpty() || fileUnitContractData.get(i).get(0).toString().isEmpty()) {
                    continue;
                }
            }

            String folderName = name + " " + folderSuffix;
            if (checkFolderNames.containsKey(folderName)) {
                String subFolderId = checkFolderNames.get(folderName);
                String fileUrl = "";
                String fileId = null;

                // Find file in subfolder
                FileList files = driveServiceHelper.findFileInSubfolderByName(subFolderId, fileToShareName);

                if (!files.getFiles().isEmpty()) {
                    fileUrl = files.getFiles().get(0).getWebViewLink();
                    fileId = files.getFiles().get(0).getId();
                }

                // Prepare Emails
                List<Object> accessRow = fileUnitsAccess.get(i);
                List<String> emails = new ArrayList<>();
                if (emailStartColIndex != -1 && emailStartColIndex < accessRow.size()) {
                    for (int k = emailStartColIndex; k < accessRow.size(); k++) {
                        String e = accessRow.get(k).toString().trim();
                        if (!e.isEmpty()) emails.add(e);
                    }
                }

                EmailDTO emailDTO = new EmailDTO();
                emailDTO.setFo(name);
                emailDTO.setTo(new HashSet<>(emails));
                emailDTO.setCc(new HashSet<>(ccEmailList));
                emailDTO.setBcc(new HashSet<>(bccEmailList));
                emailDTO.setSubject(createEmailSubject(defaultEmailContent, tradeName, name));
                emailDTO.setBody(createEmailBody(defaultEmailContent, name, fileUrl, tradeName, targetWeek));

                System.out.println("Processing email for: " + name);

                // Send Email
                List<String> record;
                if (isExternal) {
                    if (fileToShareSettingsMap == null) {
                        fileToShareSettingsMap = this.scriptSettingLoader.getSettingsMap(fileId);
                    }
                    record = sendEmailWithChartSendGrid(emailDTO, fileId, fileUrl, fileToShareSettingsMap);

                } else {
                    record = sendEmailSendGrid(emailDTO);
                }
                notificationHistoryRecord.add(new ArrayList<>(record));
            }
        }

        // 4. Update History Log
        String historyRange = isExternal ? wfScriptSetting.getAsString("ae1_NotificationSettings_NotificationHistoryRange_External") : wfScriptSetting.getAsString("ae1_NotificationSettings_NotificationHistoryRange");
        this.sheetServiceHelper.clearRange(workFileId, historyRange);
        this.sheetServiceHelper.outputAPIRows(historyRange, notificationHistoryRecord, workFileId);


    }


    private String createEmailSubject(List<List<Object>> emailContent, String tradeName, String foName) {
        String today = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (List<Object> row : emailContent) {
            if (row.size() >= 2 && "subject".equalsIgnoreCase(row.get(0).toString())) {
                return row.get(1).toString()
                        .replace("<TRADE NAME>", tradeName)
                        .replace("<FO NAME>", foName)
                        .replace("<Date>", today);
            }
        }
        return "";
    }

    private String createEmailBody(List<List<Object>> emailContent, String foName, String fileLink, String tradeName, String targetWeek) {
        String today = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (List<Object> row : emailContent) {
            if (row.size() >= 2 && "body".equalsIgnoreCase(row.get(0).toString())) {
                return row.get(1).toString()
                        .replace("<FO NAME>", foName)
                        .replace("<FO INPUT LINK>", fileLink)
                        .replace("<Trade Name>", tradeName)
                        .replace("<TargetWeek>", targetWeek)
                        .replace("<Date>", today)
                        .replace("\r\n", "\n").replace("\n", "<br>");
            }
        }
        return "";
    }

    // --- SendGrid Logic ---
    public List<String> sendEmailSendGrid(EmailDTO emailData) {
        String currentDate = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"));

        try {
            if (emailData.getTo() == null || emailData.getTo().isEmpty()) {
                return Arrays.asList(emailData.getFo(), HISTORY_SKIPPED, String.join(",", emailData.getTo()), "");
            }
            Set<String> toSet = emailData.getTo();
            Set<String> ccSet = emailData.getCc();
            Set<String> bccSet = emailData.getBcc();
            ccSet.removeAll(toSet);
            bccSet.removeAll(toSet);
            bccSet.removeAll(ccSet);
//            String safeBody = (emailData.getBody() != null ? emailData.getBody() : "").replace("\r\n", "\n").replace("\n", "<br>");
            this.sendGridService.sendEmail(toSet, ccSet, bccSet, emailData.getSubject(), emailData.getBody(), null);
//            return Arrays.asList(emailData.getFo(), currentDate, emailData.getTo(), emailData.getCc(), emailData.getBcc());
            return Arrays.asList(emailData.getFo(), currentDate, String.join(",", emailData.getTo()), String.join(",", emailData.getCc()), String.join(",", emailData.getBcc()));

        } catch (Exception e) {
            e.printStackTrace();
            return Arrays.asList(emailData.getFo(), HISTORY_ERROR, String.join(",", emailData.getTo()), String.join(",", emailData.getCc()), String.join(",", emailData.getBcc()));
        }
    }

    public List<String> sendEmailWithChartSendGrid(EmailDTO emailData, String fileId, String fileUrl, ScriptSetting ftsSettingsMap) {
        String currentDate = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"));
        try {
            List<List<Object>> pivotData = this.sheetServiceHelper.inputAPI(fileId, ftsSettingsMap.getAsString("fileToShare_CreateChart_PivotDataRange_1"));
            pivotData = Utilities.transposeList(pivotData, true);
            List<String> weeks = pivotData.get(0).stream().map(Object::toString).toList();
            List<Double> firmTeuList = pivotData.get(1).stream().map(o -> Double.parseDouble(o.toString())).toList();
            List<Double> planTeuList = pivotData.get(2).stream().map(o -> Double.parseDouble(o.toString())).toList();
            List<Double> planUtilTeuList = pivotData.get(3).stream().map(o -> Double.parseDouble(o.toString())).toList();
            List<Double> firmTeuNonApList = pivotData.get(4).stream().map(o -> Double.parseDouble(o.toString())).toList();
            byte[] chartImageBytes = this.chartService.generateChartForExternalEmail(weeks, firmTeuList, planTeuList, planUtilTeuList, firmTeuNonApList);
            this.sendGridService.sendReportEmail(emailData.getTo(), emailData.getCc(), emailData.getBcc(), emailData.getSubject(), emailData.getBody(), chartImageBytes);

            return Arrays.asList(emailData.getFo(), currentDate, String.join(",", emailData.getTo()), String.join(",", emailData.getCc()), String.join(",", emailData.getBcc()));
        } catch (Exception e) {
            e.printStackTrace();
            return Arrays.asList(emailData.getFo(), HISTORY_ERROR, String.join(",", emailData.getTo()), String.join(",", emailData.getCc()), String.join(",", emailData.getBcc()));
        }
    }

    private String nameTargetWeek(List<List<Object>> targetWeekFull) {
        List<Integer> nums = new ArrayList<>();
        for (List<Object> row : targetWeekFull) {
            for (Object cell : row) {
                try {
                    if (cell != null && !cell.toString().isEmpty())
                        nums.add(Integer.parseInt(cell.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (nums.isEmpty()) return "";
        int min = Collections.min(nums);
        int max = Collections.max(nums);
        return "W" + (min % 100) + "-W" + (max % 100);
    }
}
