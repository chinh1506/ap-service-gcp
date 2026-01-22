package com.cyberlogitec.ap_service_gcp.dto.bkg;

import com.cyberlogitec.ap_service_gcp.dto.FolderStructure;
import com.cyberlogitec.ap_service_gcp.util.GlobalSettingBKG;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateFileToShareDTO {
    private String workFileId;
    private String toShareFolderId;
    private int totalElement;
    private int taskCount;
    private String fileToShareId;
    private String fileToShareName;
    private String workFilePart2Id;
    private FolderStructure folderStructure;
    private GlobalSettingBKG gs;
    private List<List<Object>> fileUnits;
    private List<List<Object>> fileUnitsAccess;
    private List<List<Object>> fileUnitContractData;
    private List<List<Object>> apBookingData;

}
