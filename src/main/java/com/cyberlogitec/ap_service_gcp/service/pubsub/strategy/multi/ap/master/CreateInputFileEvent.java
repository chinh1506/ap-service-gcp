package com.cyberlogitec.ap_service_gcp.service.pubsub.strategy.multi.ap.master;

import com.cyberlogitec.ap_service_gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap_service_gcp.dto.GroupedDataDTO;
import com.cyberlogitec.ap_service_gcp.model.JobCache;
import com.cyberlogitec.ap_service_gcp.service.CloudRunJobService;
import com.cyberlogitec.ap_service_gcp.service.helper.GcsService;
import com.cyberlogitec.ap_service_gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod.EventContext;
import com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod.EventPlugin;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateInputFileEvent  implements EventPlugin {
    private final GcsService gcsService;
    private final ObjectMapper objectMapper;
    private final SheetServiceHelper sheetServiceHelper;
    private final CloudRunJobService cloudRunJobService;


    @Override
    public String getEventName() {
        return "CreateInputFileJobStrategy";
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

        List<DataToWriteDTO> allDataToWriteDTOs = new ArrayList<>();
        List<String> fileNamesToDelete = new ArrayList<>();
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
//        this.gcsService.deleteMultipleFiles(fileNamesToDelete);
//        this.cloudRunJobService.deleteJobCache(executionName);

    }
}
