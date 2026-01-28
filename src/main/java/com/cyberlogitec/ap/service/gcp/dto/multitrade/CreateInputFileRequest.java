package com.cyberlogitec.ap.service.gcp.dto.multitrade;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateInputFileRequest {
    Integer totalElement;
    Integer taskCount;
    String workFileId;
    String file2TemplateId;
    String file0Id;
    String file1Id;
    String file3Id;
    String file2NamePartial;
    Integer numOfContractOffice;
    List<String> masterGHQRHQEditors;
    List<String> additionalInputFiles;
    List<List<Object>> contractOfficesAndLinks;
    List<List<Object>> contractOfficesAndLinksNoCode;
    List<List<Object>> contractOfficesAndLinksAll;
    List<List<Integer>> ae2FirstApLockRanges;
    List<List<Integer>> ae2FirstAddApLockRanges;
    List<List<Integer>> ae2FirstApExpandRanges;
    List<List<Integer>> ae2FirstAddApExpandRanges;

}
