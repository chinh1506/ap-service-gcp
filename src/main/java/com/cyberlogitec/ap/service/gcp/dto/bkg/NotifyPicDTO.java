package com.cyberlogitec.ap.service.gcp.dto.bkg;

import com.cyberlogitec.ap.service.gcp.dto.FolderStructureDTO;
import com.cyberlogitec.ap.service.gcp.util.ScriptSetting;
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
    private Boolean isExternal;
    private FolderStructureDTO folderStructure;
    private ScriptSetting wfScriptSetting;
    private List<List<Object>> fileUnits;
    private List<List<Object>> ccEmailListRaw;
    private List<List<Object>> bccEmailListRaw;
    private List<List<Object>> defaultEmailContent;
    private List<List<Object>> targetWeekFull;
    private List<List<Object>> fileUnitsAccess;
    private List<List<Object>> fileUnitContractData;

}
