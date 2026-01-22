package com.cyberlogitec.ap_service_gcp.service.pubsub.strategy.aebooking;

import com.cyberlogitec.ap_service_gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap_service_gcp.dto.GroupedDataDTO;
import com.cyberlogitec.ap_service_gcp.model.JobCache;
import com.cyberlogitec.ap_service_gcp.service.CloudRunJobService;
import com.cyberlogitec.ap_service_gcp.service.GcsService;
import com.cyberlogitec.ap_service_gcp.service.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod.EventContext;
import com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod.EventPlugin;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
}
