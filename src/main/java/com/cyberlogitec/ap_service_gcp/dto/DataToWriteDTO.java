package com.cyberlogitec.ap_service_gcp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DataToWriteDTO {
    private String fileId;
    private String range;
    private List<List<Object>> data;
}
