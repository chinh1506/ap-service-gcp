package com.cyberlogitec.ap.service.gcp.dto.bkg;

import com.cyberlogitec.ap.service.gcp.util.ScriptSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotifyToPicRequest {
    private String toShareFolderId;
    private String workFileId;
    private Boolean isExternal;
    private int taskCount;
    private int totalElement;
    private ScriptSetting wfSctiptSetting;
}
