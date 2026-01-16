package com.cyberlogitec.ap_service_gcp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public  class FolderInfo {
    String id;
    String url;

    public FolderInfo(String id, String url) {
        this.id = id;
        this.url = url;
    }
}
