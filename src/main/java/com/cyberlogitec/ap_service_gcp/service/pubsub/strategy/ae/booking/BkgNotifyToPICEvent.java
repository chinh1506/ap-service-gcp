package com.cyberlogitec.ap_service_gcp.service.pubsub.strategy.ae.booking;

import com.cyberlogitec.ap_service_gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap_service_gcp.dto.GroupedDataDTO;
import com.cyberlogitec.ap_service_gcp.dto.bkg.NotifyPicDTO;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.model.JobCache;
import com.cyberlogitec.ap_service_gcp.service.CloudRunJobService;
import com.cyberlogitec.ap_service_gcp.service.helper.GcsService;
import com.cyberlogitec.ap_service_gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod.EventContext;
import com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod.EventPlugin;
import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BkgNotifyToPICEvent implements EventPlugin {
    private final ObjectMapper objectMapper;
    private final GcsService gcsService;
    private final SheetServiceHelper sheetServiceHelper;
    private final CloudRunJobService cloudRunJobService;

    @Override
    public String getEventName() {
        return "BkgNotifyToPIC";
    }

    @Override
    public void execute(EventContext context) throws Exception {
        JobCache jobCache = (JobCache) context.getPayload();
        String jobId = jobCache.getJobId();
        String executionName = jobCache.getExecutionName();
        Integer succeeded = context.getPubSubMessage().getProtoPayload().getResponse().getStatus().getSucceededCount();
        Integer failed = context.getPubSubMessage().getProtoPayload().getResponse().getStatus().getFailedCount();

        if (succeeded == null) succeeded = 0;
        if (failed == null) failed = 0;

        byte[] jobBytes = this.gcsService.getFile(jobId);
        JobContext jobContext = this.objectMapper.readValue(jobBytes, JobContext.class);
        NotifyPicDTO payload = objectMapper.convertValue(jobContext.getPayload(), NotifyPicDTO.class);
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
            String fileName = jobId + i;
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

        fileNamesToDelete.add(jobId);
        this.gcsService.deleteMultipleFiles(fileNamesToDelete);
        this.cloudRunJobService.deleteJobCache(executionName);
    }
}
