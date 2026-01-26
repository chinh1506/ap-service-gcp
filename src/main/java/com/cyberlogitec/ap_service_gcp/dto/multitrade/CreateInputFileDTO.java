package com.cyberlogitec.ap_service_gcp.dto.multitrade;

import lombok.Data;

@Data
public class CreateInputFileDTO {

    private String inputFileTemplateId;
    private String inputFolderId;
    private String inputFileName;

    private String file0Id;
    private String file1Id;
    private String file2Id;
    private String file3Id;
    private String file4Id;
}
