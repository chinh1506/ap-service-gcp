package com.cyberlogitec.ap.service.gcp.service.pubsub.strategy.ae.booking;

import com.cyberlogitec.ap.service.gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap.service.gcp.dto.GroupedDataDTO;
import com.cyberlogitec.ap.service.gcp.dto.bkg.NotifyPicDTO;
import com.cyberlogitec.ap.service.gcp.job.extension.JobContext;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.cyberlogitec.ap.service.gcp.model.WorkflowStatus;
import com.cyberlogitec.ap.service.gcp.repository.WorkflowStateRepository;
import com.cyberlogitec.ap.service.gcp.service.helper.GcsService;
import com.cyberlogitec.ap.service.gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod.EventContext;
import com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod.EventPlugin;
import com.cyberlogitec.ap.service.gcp.util.ScriptSetting;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BkgNotifyToPICEvent implements EventPlugin {
    private final ObjectMapper objectMapper;
    private final GcsService gcsService;
    private final SheetServiceHelper sheetServiceHelper;
    private final WorkflowStateRepository workflowStateRepository;

    @Override
    public String listeningOnJob() {
        return "BkgNotifyToPIC";
    }

    @Override
    public void handle(EventContext context) throws Exception {
        WorkflowState workflowState = context.getWorkflowState();
        Integer succeeded = context.getPubSubMessage().getProtoPayload().getResponse().getStatus().getSucceededCount();
        Integer failed = context.getPubSubMessage().getProtoPayload().getResponse().getStatus().getFailedCount();

        if (succeeded == null) succeeded = 0;
        if (failed == null) failed = 0;

        byte[] jobBytes = this.gcsService.getFile(workflowState.getCurrentStepDataKey());
        JobContext<NotifyPicDTO> jobContext = this.objectMapper.readValue(jobBytes, new TypeReference<JobContext<NotifyPicDTO>>() {
        });
        NotifyPicDTO payload = jobContext.getPayload();
        ScriptSetting wfScriptSetting = payload.getWfScriptSetting();

        DataToWriteDTO status = DataToWriteDTO.builder()
                .range(wfScriptSetting.getAsString("control_MakeCopy_Status_External"))
                .data(List.of(List.of("Completed")))
                .fileId(payload.getWorkFileId())
                .build();
        DataToWriteDTO scriptTime = DataToWriteDTO.builder()
                .range(wfScriptSetting.getAsString("control_MakeCopy_Timestamp_External"))
                .data(List.of(List.of(LocalDateTime.now().toString())))
                .fileId(payload.getWorkFileId())
                .build();


        List<DataToWriteDTO> allDataToWriteDTOs = new ArrayList<>();
        List<String> fileNamesToDelete = new ArrayList<>();
        allDataToWriteDTOs.add(status);
        allDataToWriteDTOs.add(scriptTime);
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

        workflowState.setStatus(WorkflowStatus.COMPLETED);
        fileNamesToDelete.add(workflowState.getCurrentStepDataKey());

        workflowState.getFileNamesToDelete().addAll(fileNamesToDelete);
        this.workflowStateRepository.save(workflowState);
    }
}
