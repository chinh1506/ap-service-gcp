package com.cyberlogitec.ap_service_gcp.dto;

import lombok.Data;

@Data
public class NotifyToPicRequest {
    private String toShareFolderId;
    private String workFileId;
}
