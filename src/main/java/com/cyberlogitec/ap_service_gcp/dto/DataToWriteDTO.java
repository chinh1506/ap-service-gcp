package com.cyberlogitec.ap_service_gcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DataToWriteDTO {
    private String fileId;
    private String range;
    private List<List<Object>> data;



}
