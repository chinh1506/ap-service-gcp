package com.cyberlogitec.ap.service.gcp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class GroupedDataDTO {
    private String fileId; // Tương ứng với "ssa"
    private List<String> ranges;
    private List<List<List<Object>>> dataList;

    public static List<GroupedDataDTO> toGroupedDataDTO(List<DataToWriteDTO> dataToWriteDTOS){
        Map<String, List<DataToWriteDTO>> groupedMap = dataToWriteDTOS.stream().collect(Collectors.groupingBy(DataToWriteDTO::getFileId));

        return groupedMap.entrySet().stream().map(entry -> {
            String currentFileId = entry.getKey();
            List<DataToWriteDTO> items = entry.getValue();

            List<String> ranges1 = items.stream()
                    .map(DataToWriteDTO::getRange)
                    .collect(Collectors.toList());

            List<List<List<Object>>> dataList1 = items.stream()
                    .map(DataToWriteDTO::getData)
                    .collect(Collectors.toList());

            // Tạo đối tượng mới
            return new GroupedDataDTO(currentFileId, ranges1, dataList1);
        }).toList();
    }
}
