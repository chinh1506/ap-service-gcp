package com.cyberlogitec.ap_service_gcp.dto.bkg;

import com.cyberlogitec.ap_service_gcp.dto.FolderStructure;
import com.cyberlogitec.ap_service_gcp.util.GlobalSettingBKG;
import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotifyPicDTO {
    private String workFileId;
    private String toShareFolderId;
    private int totalElement;
    private int taskCount;
    private boolean isExternal;
    private FolderStructure folderStructure;
    private ScriptSetting wfScriptSetting;
    private List<List<Object>> fileUnits;
    private List<List<Object>> ccEmailListRaw;
    private List<List<Object>> bccEmailListRaw;
    private List<List<Object>> defaultEmailContent;
    private List<List<Object>> targetWeekFull;
    private List<List<Object>> fileUnitsAccess;
    private List<List<Object>> fileUnitContractData;

}
