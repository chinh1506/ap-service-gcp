package com.cyberlogitec.ap.service.gcp.dto.multitrade;

import com.cyberlogitec.ap.service.gcp.util.ScriptSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateInputFileDTO {
    int totalElement;
    int taskCount;
    Integer numOfContractOffice;
    String file2TemplateId; // ID file template
    String file0Id;
    String file1Id;
    String file3Id;
    String file2NamePartial;
    ScriptSetting masterScriptSetting;
    ScriptSetting file3ScriptSetting;
    ScriptSetting file2TemplateScriptSetting; // Cần truyền map này vào
    List<String> additionalInputFile;
    List<String> masterGHQRHQEditors;
    List<List<Object>> contractOfficesAndLinks;
    List<List<Object>> contractOfficesAndLinksNoCode;
    List<List<Object>> contractOfficesAndLinksAll;
    List<List<Integer>> ae2FirstApLockRanges;
    List<List<Integer>> ae2FirstAddApLockRanges;
    List<List<Integer>> ae2FirstApExpandRanges;
    List<List<Integer>> ae2FirstAddApExpandRanges;
}
