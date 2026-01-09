package com.cyberlogitec.ap_service_gcp.dto;

import com.cyberlogitec.ap_service_gcp.model.FolderStructure;
import com.cyberlogitec.ap_service_gcp.util.GlobalSettingBKG;
import lombok.Builder;
import lombok.Data;

import java.util.List;

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
    private FolderStructure folderStructure;
    private GlobalSettingBKG gs;
    private List<List<Object>> fileUnits;
    private List<List<Object>> fileUnitsAccess;
    private List<List<Object>> fileUnitContractData;


}
