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
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
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

    public void prepareToCreateChildFoldersExternal(Object payload) throws IOException {
        CreateFileExternalRequest createFileExternalRequest = this.objectMapper.convertValue(payload, CreateFileExternalRequest.class);
        FolderStructure existingStructure = this.driveServiceHelper.getExistingFolderStructure(createFileExternalRequest.getToShareFolderId());

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
        context.setTaskCount(createFileExternalRequest.getTaskCount());
        context.setPayload(createFileExternalRequest);
        Utilities.logMemory("Before runCloudRunJob");
        this.cloudRunJobService.runJob("CreateChildFoldersExternal", context);
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

    private static final String SENDGRID_API_KEY = "YOUR_SENDGRID_KEY";
    private static final String SENDER_EMAIL = "sender@example.com";
    private static final String SENDER_NAME = "Sender Name";

    // Constants trạng thái
    private static final String STATUS_SUCCESS = "Success";
    private static final String STATUS_ERROR_UNDEFINED = "Error";
    private static final String HISTORY_SKIPPED = "Skipped";
    private static final String HISTORY_ERROR = "Error Sending";

    public String notifyToPICButton(String toShareFolderId, GlobalSettings gs, boolean isExternal, Map<String, String> fileToShareSettingsMap) {
        String scriptStatus = STATUS_SUCCESS;

        try {
            // 1. Initialization
            String fileNameToShare = gs.getSetting("bookingComparison_CreatefileToShare_FileName");
            String salesWeek = gs.getSetting("control_MakeCopy_SalesWeek");
            String fileToShareName = fileNameToShare + "_" + salesWeek;

            String coEditorsSheetName = isExternal ? "Distribution List - External" : "Distribution List";
            String distListRange = isExternal ? gs.getSetting("control_MakeCopy_DistList_DataRange_External") : gs.getSetting("control_MakeCopy_DistList_DataRange");

            // Giả định inputAPI trả về List<List<Object>>
            List<List<Object>> fileUnits = this.sheetServiceHelper.inputAPI(gs.workFileId, distListRange);

            String tradeName = gs.getSetting("control_MakeCopy_TradeName");
            String folderSuffix = String.format("(%s_Booking Comparison Tool)", tradeName);

            String ccEmailRange = isExternal ? gs.getSetting("ae1_NotificationSettings_CClist_External") : gs.getSetting("ae1_NotificationSettings_CClist");
            String bccEmailRange = isExternal ? gs.getSetting("ae1_NotificationSettings_BCClist_External") : gs.getSetting("ae1_NotificationSettings_BCClist");

            List<List<Object>> ccEmailListRaw = this.sheetServiceHelper.inputAPI(gs.workFileId, ccEmailRange);
            List<List<Object>> bccEmailListRaw = this.sheetServiceHelper.inputAPI(gs.workFileId, bccEmailRange);

            // Flatten lists
            List<String> ccEmailList = flattenList(ccEmailListRaw);
            List<String> bccEmailList = flattenList(bccEmailListRaw);

            String emailContentRange = isExternal ? gs.getSetting("ae1_NotificationSettings_EmailContentRange_External") : gs.getSetting("ae1_NotificationSettings_EmailContentRange");
            List<List<Object>> defaultEmailContent = this.sheetServiceHelper.inputAPI(gs.workFileId, emailContentRange);

            String targetWeekRange = gs.getSetting("ae1_NotificationSettings_targetWeek");
            List<List<Object>> targetWeekFull = this.sheetServiceHelper.inputAPI(gs.workFileId, targetWeekRange);
            String targetWeek = nameTargetWeek(targetWeekFull);

            String foEditorRange = isExternal ? gs.getSetting("control_MakeCopy_FOEditors_DataRange_External") : gs.getSetting("control_MakeCopy_FOEditors_DataRange");
            List<List<Object>> fileUnitsAccess = this.sheetServiceHelper.inputAPI(gs.workFileId, foEditorRange);

            // Lấy header để tìm index cột email (giả lập logic JS)
            String foSettingsTitleRow = isExternal ? gs.getSetting("control_MakeCopy_FOSettings_TitleRow_External") : gs.getSetting("control_MakeCopy_FOSettings_TitleRow");
            List<Object> headers = gsUtils.readRow(gs.workFileSS, coEditorsSheetName, Integer.parseInt(foSettingsTitleRow));

            String emailColName = isExternal ? gs.getSetting("control_MakeCopy_EmailStartColName_External") : gs.getSetting("control_MakeCopy_EmailStartColName");
            int emailStartColIndex = headers.indexOf(emailColName);

            String fileUnitContractRange = gs.getSetting("control_MakeCopy_FileUnitContract_DataRange_External");
            List<List<Object>> fileUnitContractData = isExternal ? this.sheetServiceHelper.inputAPI(gs.workFileId, fileUnitContractRange) : new ArrayList<>();

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

                    // Find file in subfolder
                    FileList files = driveServiceHelper.findFileInSubfolderByName(subFolderId, fileToShareName);

                    if (!files.getFiles().isEmpty()) {
                        fileUrl = files.getFiles().get(0).getWebViewLink();
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

                    EmailDTO emailToBeSent = new EmailDTO();
                    emailToBeSent.fo = name;
                    emailToBeSent.to = String.join(",", emails);
                    emailToBeSent.cc = String.join(",", ccEmailList);
                    emailToBeSent.bcc = String.join(",", bccEmailList);
                    emailToBeSent.subject = createEmailSubject(defaultEmailContent, tradeName, name);
                    emailToBeSent.body = createEmailBody(defaultEmailContent, name, fileUrl, tradeName, targetWeek);

                    System.out.println("Processing email for: " + name);

                    // Send Email
                    List<Object> record;
                    if (isExternal) {
                        record = sendEmailWithChartSendGrid(emailToBeSent, fileUrl, gs, fileToShareSettingsMap);
                    } else {
                        record = sendEmailSendGrid(emailToBeSent);
                    }
                    notificationHistoryRecord.add(record);
                }
            }

            // 4. Update History Log
            String historyRange = isExternal ? gs.getSetting("ae1_NotificationSettings_NotificationHistoryRange_External") : gs.getSetting("ae1_NotificationSettings_NotificationHistoryRange");
            this.sheetServiceHelper.clearRange(gs.workFileId, historyRange);
            this.sheetServiceHelper.outputAPIRows(historyRange, notificationHistoryRecord,gs.workFileId);

        } catch (Exception e) {
            e.printStackTrace();
            scriptStatus = STATUS_ERROR_UNDEFINED;
        }

        return scriptStatus;
    }

    // --- Helper Methods ---

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
                        .replace("<Date>", today);
            }
        }
        return "";
    }

    // --- SendGrid Logic ---

    public List<Object> sendEmailSendGrid(EmailDTO emailData) {
        String currentDate = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"));

        try {
            if (emailData.to == null || emailData.to.isEmpty()) {
                return Arrays.asList(emailData.fo, HISTORY_SKIPPED, emailData.to, "");
            }

            // Parse emails
            Set<String> toSet = parseEmails(emailData.to);
            Set<String> ccSet = parseEmails(emailData.cc);
            Set<String> bccSet = parseEmails(emailData.bcc);

            // Deduplicate
            ccSet.removeAll(toSet);
            bccSet.removeAll(toSet);
            bccSet.removeAll(ccSet);

            // Prepare Payload Map
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> personalization = new HashMap<>();

            personalization.put("to", toSet.stream().map(e -> Map.of("email", e)).collect(Collectors.toList()));
            if (!ccSet.isEmpty()) personalization.put("cc", ccSet.stream().map(e -> Map.of("email", e)).collect(Collectors.toList()));
            if (!bccSet.isEmpty()) personalization.put("bcc", bccSet.stream().map(e -> Map.of("email", e)).collect(Collectors.toList()));

            payload.put("personalizations", Collections.singletonList(personalization));
            payload.put("from", Map.of("email", SENDER_EMAIL, "name", SENDER_NAME));
            payload.put("subject", emailData.subject);

            String safeBody = (emailData.body != null ? emailData.body : "").replace("\r\n", "\n").replace("\n", "<br>");
            payload.put("content", Collections.singletonList(Map.of("type", "text/html", "value", safeBody)));

            // Call API
            callSendGridApi(payload);

            return Arrays.asList(emailData.fo, currentDate, emailData.to, emailData.cc, emailData.bcc);

        } catch (Exception e) {
            e.printStackTrace();
            return Arrays.asList(emailData.fo, HISTORY_ERROR, emailData.to, emailData.cc, emailData.bcc);
        }
    }

    public List<Object> sendEmailWithChartSendGrid(EmailDTO emailData, String fileUrl, GlobalSettings gs, Map<String, String> settingsMap) {
        // NOTE: Generating charts from Sheets in Java is complex.
        // This function assumes you have a way to generate the Chart Image as a byte array (imageBytes).
        // In real Java backend, you would read the raw data from 'pivotSourceDataRange',
        // use JFreeChart/XChart to render a PNG, and use that.

        String currentDate = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"));
        try {
            // 1. Pivot Table Creation (Calling the method below)
            createPivotTableForChart(fileUrl, settingsMap.get("fileToShare_CreateChart_PivotDataRange_1"));

            // 2. Generate/Get Images (Logo & Chart)
            // Warning: Google Sheets API v4 does NOT support 'getBlob()' for charts.
            // You must generate the chart locally in Java based on data.
//            byte[] chartImageBytes = generateChartImageLocally(fileUrl); // Mock function
            byte[] chartImageBytes = null; // Mock function
//            byte[] logoImageBytes = getLogoFromDrive(gs.getSetting("createfileToShare_LogoFolderLink")); // Mock function
            byte[] logoImageBytes =null;// Mock function

            String chartBase64 = Base64.getEncoder().encodeToString(chartImageBytes);
            String logoBase64 = Base64.getEncoder().encodeToString(logoImageBytes);

            // 3. Build Body & Payload (similar to above but with attachments)
            // ... (HTML construction logic matches GAS) ...

            Map<String, Object> payload = new HashMap<>();
            // ... (Add personalizations, from, subject as above) ...

            // Attachments (Inline)
            List<Map<String, Object>> attachments = new ArrayList<>();
            attachments.add(Map.of(
                    "content", chartBase64,
                    "filename", "Chart1.png",
                    "type", "image/png",
                    "disposition", "inline",
                    "content_id", "chart1"
            ));
            attachments.add(Map.of(
                    "content", logoBase64,
                    "filename", "logo.png",
                    "type", "image/png",
                    "disposition", "inline",
                    "content_id", "logo"
            ));
            payload.put("attachments", attachments);

            callSendGridApi(payload);

            return Arrays.asList(emailData.fo, currentDate, emailData.to, emailData.cc, emailData.bcc);
        } catch (Exception e) {
            e.printStackTrace();
            return Arrays.asList(emailData.fo, HISTORY_ERROR, emailData.to, emailData.cc, emailData.bcc);
        }
    }

    // --- Pivot Table Logic (Google Sheets API v4) ---
    // Đây là phần phức tạp nhất khi chuyển từ GAS sang Java
    public void createPivotTableForChart(String spreadsheetId, String sourceRange) throws IOException {
        // Logic:
        // 1. Get Sheet ID by Name
        // 2. Clear old Pivot sheet or Create new
        // 3. Construct BatchUpdate request to add Pivot Table

        // Code ở đây sẽ rất dài (khoảng 100-200 dòng) để định nghĩa Rows, Columns, Values, Filters
        // bằng các object của Google Sheets API (PivotGroup, PivotValue, etc.).
        // Dưới đây là ví dụ cấu trúc:

        /*
        PivotTable pivotTable = new PivotTable()
            .setSource(new GridRange()...)
            .setRows(Arrays.asList(
                new PivotGroup().setSourceColumnOffset(41).setShowTotals(false), // Row Group 41
                new PivotGroup().setSourceColumnOffset(24).setShowTotals(false)  // Row Group 24
            ))
            .setValues(Arrays.asList(
                 new PivotValue().setSourceColumnOffset(26).setSummarizeFunction("SUM"), // Firm TEU
                 new PivotValue().setSourceColumnOffset(25).setSummarizeFunction("SUM")  // Plan TEU
            ));

        UpdateCellsRequest updateCells = new UpdateCellsRequest()
            .setRows(Arrays.asList(new RowData().setValues(Arrays.asList(new CellData().setPivotTable(pivotTable)))))
            .setStart(new GridCoordinate().setSheetId(pivotSheetId).setRowIndex(20).setColumnIndex(5)); // F21

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(Arrays.asList(new Request().setUpdateCells(updateCells)));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
        */
    }

    // --- Utilities ---

    private void callSendGridApi(Map<String, Object> payload) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(payload);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Authorization", "Bearer " + SENDGRID_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 202) {
            throw new RuntimeException("SendGrid Error: " + response.statusCode() + " " + response.body());
        }
    }

    private Set<String> parseEmails(String emailStr) {
        if (emailStr == null || emailStr.isEmpty()) return new HashSet<>();
        return Arrays.stream(emailStr.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String nameTargetWeek(List<List<Object>> targetWeekFull) {
        List<Integer> nums = new ArrayList<>();
        for(List<Object> row : targetWeekFull) {
            for(Object cell : row) {
                try {
                    if(cell != null && !cell.toString().isEmpty())
                        nums.add(Integer.parseInt(cell.toString()));
                } catch(NumberFormatException e){}
            }
        }
        if(nums.isEmpty()) return "";
        int min = Collections.min(nums);
        int max = Collections.max(nums);
        return "W" + (min % 100) + "-W" + (max % 100);
    }

    private List<String> flattenList(List<List<Object>> raw) {
        List<String> result = new ArrayList<>();
        if (raw != null) {
            for (List<Object> row : raw) {
                for (Object item : row) {
                    if (item != null && !item.toString().isEmpty()) {
                        result.add(item.toString());
                    }
                }
            }
        }
        return result;
    }

    // Inner class DTO
    public static class EmailDTO {
        String fo;
        String to;
        String cc;
        String bcc;
        String subject;
        String body;
    }

    // Placeholder class cho Settings
    public static class GlobalSettings {
        public String workFileId;
        public String workFileSS; // ID
        public Map<String, String> settingsMap;
        public String getSetting(String key) { return settingsMap.get(key); }
    }



}
