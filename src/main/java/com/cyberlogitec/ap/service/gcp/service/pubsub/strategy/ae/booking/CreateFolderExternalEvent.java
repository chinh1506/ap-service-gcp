package com.cyberlogitec.ap.service.gcp.service.pubsub.strategy.ae.booking;

import com.cyberlogitec.ap.service.gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap.service.gcp.dto.GroupedDataDTO;
import com.cyberlogitec.ap.service.gcp.dto.bkg.CreateFileToShareDTO;
import com.cyberlogitec.ap.service.gcp.dto.bkg.NotifyToPicRequest;
import com.cyberlogitec.ap.service.gcp.job.extension.JobContext;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.cyberlogitec.ap.service.gcp.model.WorkflowStatus;
import com.cyberlogitec.ap.service.gcp.repository.WorkflowStateRepository;
import com.cyberlogitec.ap.service.gcp.service.AeDomiBookingService;
import com.cyberlogitec.ap.service.gcp.service.helper.GcsService;
import com.cyberlogitec.ap.service.gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod.EventContext;
import com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod.EventPlugin;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateFolderExternalEvent implements EventPlugin {
    private final ObjectMapper objectMapper;
    private final GcsService gcsService;
    private final SheetServiceHelper sheetServiceHelper;
    private final AeDomiBookingService bookingJobService;
    private final WorkflowStateRepository workflowStateRepository;

    @Override
    public String listeningOnJob() {
        return "CreateChildFoldersExternal";
    }

    @Override
    public void handle(EventContext context) throws Exception {
        WorkflowState workflowState = context.getWorkflowState();
        Integer succeeded = context.getPubSubMessage().getProtoPayload().getResponse().getStatus().getSucceededCount();
        Integer failed = context.getPubSubMessage().getProtoPayload().getResponse().getStatus().getFailedCount();
        if (succeeded == null) succeeded = 0;
        if (failed == null) failed = 0;
        workflowState.setStatus(WorkflowStatus.COMPLETED);

        List<DataToWriteDTO> allDataToWriteDTOs = new ArrayList<>();
        List<String> fileNamesToDelete = new ArrayList<>();
        for (int i = 0; i < succeeded + failed; i++) {
            String fileName = workflowState.getCurrentStepDataKey() + i;
            byte[] bytes = this.gcsService.getFile(fileName);
            List<DataToWriteDTO> list = this.objectMapper.readValue(bytes, new TypeReference<List<DataToWriteDTO>>() {
            });
            allDataToWriteDTOs.addAll(list);
            fileNamesToDelete.add(fileName);
        }

        List<GroupedDataDTO> result = GroupedDataDTO.toGroupedDataDTO(allDataToWriteDTOs);

        result.forEach(groupedDataDTO -> {
            try {
                this.sheetServiceHelper.batchOutputAPIRows(groupedDataDTO.getRanges(), groupedDataDTO.getDataList(), groupedDataDTO.getFileId());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        byte[] bytes = this.gcsService.getFile(workflowState.getCurrentStepDataKey());
        JobContext<CreateFileToShareDTO> jobContext = this.objectMapper.readValue(bytes, new TypeReference<JobContext<CreateFileToShareDTO>>() {
        });
        CreateFileToShareDTO createFileToShareDTO = jobContext.getPayload();

        NotifyToPicRequest notifyToPicRequest = NotifyToPicRequest.builder()
                .wfSctiptSetting(createFileToShareDTO.getGs().getScriptSettingsPart1())
                .isExternal(true)
                .totalElement(createFileToShareDTO.getTotalElement())
                .taskCount(jobContext.getTaskCount())
                .toShareFolderId(createFileToShareDTO.getToShareFolderId())
                .workFileId(createFileToShareDTO.getWorkFileId())
                .build();

        fileNamesToDelete.add(workflowState.getCurrentStepDataKey());
        workflowState.getFileNamesToDelete().addAll(fileNamesToDelete);
        this.workflowStateRepository.save(workflowState);
        bookingJobService.notifyToPIC(notifyToPicRequest, workflowState);
    }
}
