package com.cyberlogitec.ap_service_gcp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateFileExternalRequest {
    private String workFileId;
    private String copyFolderId;
    private int startRow;
    private int endRow;
    private String fileToShareId;
    private String fileToShareName;
    private String workFilePart2Id;
}
