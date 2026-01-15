package com.cyberlogitec.ap_service_gcp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GroupedDataDTO {
    private String fileId; // Tương ứng với "ssa"
    private List<String> ranges;
    private List<List<List<Object>>> dataList;
}
