package com.cyberlogitec.ap.service.gcp.job.strategy.bkg.aedomi;

import com.cyberlogitec.ap.service.gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap.service.gcp.dto.EmailDTO;
import com.cyberlogitec.ap.service.gcp.dto.FolderInfoDTO;
import com.cyberlogitec.ap.service.gcp.dto.bkg.NotifyPicDTO;
import com.cyberlogitec.ap.service.gcp.job.extension.JobContext;
import com.cyberlogitec.ap.service.gcp.job.extension.JobPlugin;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.cyberlogitec.ap.service.gcp.service.helper.*;
import com.cyberlogitec.ap.service.gcp.util.ScriptSetting;
import com.cyberlogitec.ap.service.gcp.util.ScriptSettingLoader;
import com.cyberlogitec.ap.service.gcp.util.TaskPartitioner;
import com.cyberlogitec.ap.service.gcp.util.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.drive.model.FileList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.cyberlogitec.ap.service.gcp.service.helper.SendGridService.HISTORY_ERROR;
import static com.cyberlogitec.ap.service.gcp.service.helper.SendGridService.HISTORY_ERROR_FILE_NOT_FOUND;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"job-prod", "job-dev"})
public class NotifyToPICStrategy implements JobPlugin {
    private final ObjectMapper objectMapper;
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ScriptSettingLoader scriptSettingLoader;
    private final GcsService gcsService;
    private final SendGridService sendGridService;
    private final ChartService chartService;

    @Override
    public String getJobName() {
        return "BkgNotifyToPIC";
    }

    @Override
    public void execute(JobContext<Object> context) throws Exception {
        System.out.println("NotifyToPICExternal logic");
        NotifyPicDTO payload = objectMapper.convertValue(context.getPayload(), NotifyPicDTO.class);
        WorkflowState workflowState = context.getWorkflowState();
        String totalTasksStr = System.getenv("CLOUD_RUN_TASK_COUNT");
        String currentTaskIndexStr = System.getenv("CLOUD_RUN_TASK_INDEX");
        TaskPartitioner.Partition partition = Utilities.getCurrentPartition(totalTasksStr, currentTaskIndexStr, payload.getTotalElement());
        if (partition == null) {
            System.exit(0);
        }

        notifyToPIC(payload, partition.start, partition.end, workflowState.getCurrentStepDataKey() + currentTaskIndexStr);
    }


    public void notifyToPIC(NotifyPicDTO notifyPicDTO, int start, int end, String taskId) throws IOException {

        List<DataToWriteDTO> allResultToWrite = new ArrayList<>();
        String workFileId = notifyPicDTO.getWorkFileId();
        Boolean isExternal = notifyPicDTO.getIsExternal();

        ScriptSetting wfScriptSetting = notifyPicDTO.getWfScriptSetting(), fileToShareSettingsMap = null;

        String fileNameToShare = wfScriptSetting.getAsString("bookingComparison_CreatefileToShare_FileName");
        String salesWeek = wfScriptSetting.getAsString("control_MakeCopy_SalesWeek");
        String fileToShareName = fileNameToShare + "_" + salesWeek;
        List<List<Object>> fileUnits = notifyPicDTO.getFileUnits();
        String tradeName = wfScriptSetting.getAsString("control_MakeCopy_TradeName");
        String folderSuffix = String.format("(%s_Booking Comparison Tool)", tradeName);
        List<List<Object>> ccEmailListRaw = notifyPicDTO.getCcEmailListRaw();
        List<List<Object>> bccEmailListRaw = notifyPicDTO.getBccEmailListRaw();
        List<String> ccEmailList = Utilities.flattenList(ccEmailListRaw);
        List<String> bccEmailList = Utilities.flattenList(bccEmailListRaw);
        List<List<Object>> defaultEmailContent = notifyPicDTO.getDefaultEmailContent();
        List<List<Object>> targetWeekFull = notifyPicDTO.getTargetWeekFull();
        String targetWeek = Utilities.nameTargetWeek(targetWeekFull);
        List<List<Object>> fileUnitsAccess = notifyPicDTO.getFileUnitsAccess();
        int emailStartColIndex = Integer.parseInt(wfScriptSetting.getAsString("control_MakeCopy_EmailStartColIndex")) - 1;
        List<List<Object>> fileUnitContractData = isExternal ? notifyPicDTO.getFileUnitContractData() : new ArrayList<>();
        System.out.println("Initialization completed");

        Map<String, FolderInfoDTO> checkFolderNames = notifyPicDTO.getFolderStructure().getFolderMap();

        List<List<Object>> notificationHistoryRecord = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            String name = (String) fileUnits.get(i).get(0);
            if (name == null || name.isEmpty()) continue;
            if (isExternal) {
                if (fileUnitContractData.size() <= i || fileUnitContractData.get(i).isEmpty() || fileUnitContractData.get(i).get(0).toString().isEmpty()) {
                    continue;
                }
            }
            String folderName = name + " " + folderSuffix;
            if (checkFolderNames.containsKey(folderName)) {
                String subFolderId = checkFolderNames.get(folderName).getId();
                String fileUrl = "";
                String fileId = null;
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
                emailDTO.setSubject(this.sendGridService.createEmailSubject(defaultEmailContent, tradeName, name));
                emailDTO.setBody(this.sendGridService.createEmailBody(defaultEmailContent, name, fileUrl, tradeName, targetWeek));
                System.out.println("Processing email for: " + name);
                List<String> record;
                if (fileId == null) {
                    record = Arrays.asList(emailDTO.getFo(), HISTORY_ERROR_FILE_NOT_FOUND, String.join(",", emailDTO.getTo()), String.join(",", emailDTO.getCc()), String.join(",", emailDTO.getBcc()));
                } else if (isExternal) {
                    if (fileToShareSettingsMap == null) {
                        fileToShareSettingsMap = this.scriptSettingLoader.getSettingsMap(fileId);
                    }
                    record = sendEmailWithChartSendGrid(emailDTO, fileId, fileToShareSettingsMap);

                } else {
                    record = this.sendGridService.sendEmailSendGrid(emailDTO);
                }
                notificationHistoryRecord.add(new ArrayList<>(record));
            }
        }
        int safeEnd = Math.min(end, fileUnits.size());
        if (start <= safeEnd) {
            String historyRange = isExternal ? wfScriptSetting.getAsString("ae1_NotificationSettings_NotificationHistoryRange_External") : wfScriptSetting.getAsString("ae1_NotificationSettings_NotificationHistoryRange");
            String rangeToWrite = Utilities.calculateSubRangeA1(historyRange, start, safeEnd + 1);
            if (rangeToWrite != null && !notificationHistoryRecord.isEmpty()) {
                allResultToWrite.add(new DataToWriteDTO(workFileId, rangeToWrite, notificationHistoryRecord));
            }
        }
        this.gcsService.uploadStreaming(taskId, allResultToWrite);
    }

    private List<String> sendEmailWithChartSendGrid(EmailDTO emailData, String fileId, ScriptSetting ftsSettingsMap) {
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
            log.error(e.getMessage());
            return Arrays.asList(emailData.getFo(), HISTORY_ERROR, String.join(",", emailData.getTo()), String.join(",", emailData.getCc()), String.join(",", emailData.getBcc()));
        }
    }
}
