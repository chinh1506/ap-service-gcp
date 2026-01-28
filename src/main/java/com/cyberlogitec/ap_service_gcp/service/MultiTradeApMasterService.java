package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.dto.multitrade.CreateInputFileDTO;
import com.cyberlogitec.ap_service_gcp.dto.multitrade.CreateInputFileRequest;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.service.helper.DriveServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.util.ScriptSettingLoader;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MultiTradeApMasterService {
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ScriptSettingLoader scriptSettingLoader;
    private final CloudRunJobService cloudRunJobService;

    public void prepareToCreateFoFiles(CreateInputFileRequest createInputFileRequest) throws IOException {


        CreateInputFileDTO createInputFileDTO = CreateInputFileDTO.builder()
                .masterGHQRHQEditors(createInputFileRequest.getMasterGHQRHQEditors())
                .contractOfficesAndLinksAll(createInputFileRequest.getContractOfficesAndLinksAll())
                .additionalInputFile(createInputFileRequest.getAdditionalInputFiles())
                .ae2FirstApLockRanges(createInputFileRequest.getAe2FirstApLockRanges())
                .ae2FirstAddApLockRanges(createInputFileRequest.getAe2FirstAddApLockRanges())
                .ae2FirstApExpandRanges(createInputFileRequest.getAe2FirstApExpandRanges())
                .ae2FirstAddApExpandRanges(createInputFileRequest.getAe2FirstAddApExpandRanges())
                .file0Id(createInputFileRequest.getFile0Id())
                .file1Id(createInputFileRequest.getFile1Id())
                .file3Id(createInputFileRequest.getFile3Id())
                .file3ScriptSetting(this.scriptSettingLoader.getSettingsMap(createInputFileRequest.getFile3Id(),"ScriptSettings"))
                .masterScriptSetting(this.scriptSettingLoader.getSettingsMap(createInputFileRequest.getWorkFileId(),"ScriptSettings"))
                .numOfContractOffice(createInputFileRequest.getNumOfContractOffice())
                .contractOfficesAndLinks(createInputFileRequest.getContractOfficesAndLinks())
                .contractOfficesAndLinksNoCode(createInputFileRequest.getContractOfficesAndLinksNoCode())
                .file2NamePartial(createInputFileRequest.getFile2NamePartial())
                .file2TemplateId(createInputFileRequest.getFile2TemplateId())
                .file2TemplateScriptSetting(this.scriptSettingLoader.getSettingsMap(createInputFileRequest.getFile2TemplateId(),"ScriptSettings"))
                .taskCount(createInputFileRequest.getTaskCount())
                .totalElement(createInputFileRequest.getTotalElement())
                .build();
        Spreadsheet spreadsheet = this.sheetServiceHelper.getSpreadsheetMetadata(createInputFileRequest.getFile2TemplateId());
        sheetServiceHelper.removeAllProtections(spreadsheet);

        JobContext context = JobContext.builder()
                .jobId(UUID.randomUUID().toString())
                .payload(createInputFileDTO)
                .taskCount(createInputFileRequest.getTaskCount())
                .build();

        this.cloudRunJobService.runJob("CreateInputFileJobStrategy", context);

    }

}
