package com.cyberlogitec.ap.service.gcp.service;

import com.cyberlogitec.ap.service.gcp.dto.FolderStructureDTO;
import com.cyberlogitec.ap.service.gcp.dto.bkg.CreateFileToShareDTO;
import com.cyberlogitec.ap.service.gcp.dto.bkg.NotifyPicDTO;
import com.cyberlogitec.ap.service.gcp.dto.bkg.NotifyToPicRequest;
import com.cyberlogitec.ap.service.gcp.job.extension.JobContext;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.cyberlogitec.ap.service.gcp.service.helper.DriveServiceHelper;
import com.cyberlogitec.ap.service.gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap.service.gcp.util.GlobalSettingBKG;
import com.cyberlogitec.ap.service.gcp.util.ScriptSetting;
import com.cyberlogitec.ap.service.gcp.util.ScriptSettingLoader;
import com.cyberlogitec.ap.service.gcp.util.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AeDomiBookingService {
    private final CloudRunJobService cloudRunJobService;
    private final ObjectMapper objectMapper;
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ScriptSettingLoader scriptSettingLoader;

    public void prepareToCreateChildFoldersExternal(Object payload) throws IOException {
        CreateFileToShareDTO createFileDTO = this.objectMapper.convertValue(payload, CreateFileToShareDTO.class);
        FolderStructureDTO existingStructure = this.driveServiceHelper.getExistingFolderStructure(createFileDTO.getToShareFolderId());

        String workFileId = createFileDTO.getWorkFileId();
        String fileToShareId = createFileDTO.getFileToShareId();

        GlobalSettingBKG gs = GlobalSettingBKG.builder()
                .scriptSettingsPart1(scriptSettingLoader.getSettingsMap(workFileId))
                .scriptSettingsPart2(scriptSettingLoader.getSettingsMap(createFileDTO.getWorkFilePart2Id()))
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

        createFileDTO.setFolderStructure(existingStructure);
        createFileDTO.setGs(gs);
        createFileDTO.setFileUnits(fileUnits);
        createFileDTO.setFileUnitsAccess(fileUnitsAccess);
        createFileDTO.setFileUnitContractData(fileUnitContractData);
        createFileDTO.setApBookingData(apBookingData);

        Utilities.logMemory("Before set data to context");
        JobContext<CreateFileToShareDTO> context = new JobContext<>();
        context.setTaskCount(createFileDTO.getTaskCount());
        context.setPayload(createFileDTO);

        Utilities.logMemory("Before runCloudRunJob");

        WorkflowState workflowState = WorkflowState.startWorkflow("CreateChildFoldersExternal", 2);
        context.setWorkflowState(workflowState);

        this.cloudRunJobService.runJob(context);
        Utilities.logMemory("After runCloudRunJob");
    }

    /**
     * PUB/SUB will call the functions that have workflowState param
     *
     * @param notifyToPicRequest
     * @param workflowState
     * @throws IOException
     */
    public void notifyToPIC(NotifyToPicRequest notifyToPicRequest, WorkflowState workflowState) throws IOException {

        String toShareFolderId = notifyToPicRequest.getToShareFolderId();
        String workFileId = notifyToPicRequest.getWorkFileId();
        boolean isExternal = notifyToPicRequest.getIsExternal();

        ScriptSetting wfScriptSetting = notifyToPicRequest.getWfSctiptSetting();
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
        List<List<Object>> ccEmailListRaw = allDataWorkFile.get(ccEmailRange);
        List<List<Object>> bccEmailListRaw = allDataWorkFile.get(bccEmailRange);
        List<List<Object>> defaultEmailContent = allDataWorkFile.get(emailContentRange);
        List<List<Object>> targetWeekFull = allDataWorkFile.get(targetWeekRange);
        List<List<Object>> fileUnitsAccess = allDataWorkFile.get(foEditorRange);
        List<List<Object>> fileUnitContractData = isExternal ? allDataWorkFile.get(fileUnitContractRange) : new ArrayList<>();

        FolderStructureDTO folderStructure = this.driveServiceHelper.getExistingFolderStructure(toShareFolderId);

        NotifyPicDTO notifyToPicDto = NotifyPicDTO.builder()
                .fileUnits(fileUnits)
                .bccEmailListRaw(bccEmailListRaw)
                .ccEmailListRaw(ccEmailListRaw)
                .defaultEmailContent(defaultEmailContent)
                .targetWeekFull(targetWeekFull)
                .fileUnitContractData(fileUnitContractData)
                .fileUnitsAccess(fileUnitsAccess)
                .folderStructure(folderStructure)
                .isExternal(isExternal)
                .taskCount(notifyToPicRequest.getTaskCount())
                .wfScriptSetting(wfScriptSetting)
                .toShareFolderId(toShareFolderId)
                .workFileId(workFileId)
                .totalElement(notifyToPicRequest.getTotalElement())
                .taskCount(notifyToPicRequest.getTaskCount())
                .build();

        workflowState.setCurrentStepName("BkgNotifyToPIC");
        workflowState.setCurrentStepNumber(2);

        JobContext<NotifyPicDTO> context = JobContext.<NotifyPicDTO>builder()
                .workflowState(workflowState)
                .payload(notifyToPicDto)
                .taskCount(notifyToPicRequest.getTaskCount())
                .build();

        this.cloudRunJobService.runJob(context);
    }

}
