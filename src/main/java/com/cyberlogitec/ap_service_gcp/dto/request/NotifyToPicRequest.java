package com.cyberlogitec.ap_service_gcp.dto.request;

import lombok.Data;

@Data
public class NotifyToPicRequest {
    private String toShareFolderId;
    private String workFileId;
    private Boolean isExternal;
    private int taskCount;
    private int totalElement;
}
