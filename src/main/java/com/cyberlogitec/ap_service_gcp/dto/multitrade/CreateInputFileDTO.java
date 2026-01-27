package com.cyberlogitec.ap_service_gcp.dto.multitrade;

import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateInputFileDTO {
    int totalElement;
    int taskCount;
    ScriptSetting masterScriptSetting;
    ScriptSetting file3ScriptSetting;
    int numOfContractOffice;
    List<List<Object>> contractOfficesAndLinks;
    List<List<Object>> contractOfficesAndLinksNoCode;
    String inputFileNamePartial;
    List<String> masterGHQRHQEditors;
    List<List<Object>> contractOfficesAndLinksAll;
    List<String> additionalInputFile;
    Map<String, String> inputFileTemplateSettingsMap; // Cần truyền map này vào
    String inputFileTemplateId; // ID file template
    String file0Id;
    String file1Id;
    String file3Id;
    List<List<Integer>> ae2FirstApLockRanges;
    List<List<Integer>> ae2FirstAddApLockRanges;
    List<List<Integer>> ae2FirstApExpandRanges;
    List<List<Integer>> ae2FirstAddApExpandRanges;
    String foEditorSheetName;
}
