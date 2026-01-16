package com.cyberlogitec.ap_service_gcp.model;

import com.cyberlogitec.ap_service_gcp.dto.FolderInfo;
import com.cyberlogitec.ap_service_gcp.job.implement.bkg.CreateChildFoldersExternal;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class FolderStructure {
    Map<String, FolderInfo> folderMap = new HashMap<>();
    Map<String, FolderInfo> archiveMap = new HashMap<>();
}
