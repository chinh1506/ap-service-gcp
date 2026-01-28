package com.cyberlogitec.ap.service.gcp.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class FolderStructureDTO {
    Map<String, FolderInfoDTO> folderMap = new HashMap<>();
    Map<String, FolderInfoDTO> archiveMap = new HashMap<>();
}
