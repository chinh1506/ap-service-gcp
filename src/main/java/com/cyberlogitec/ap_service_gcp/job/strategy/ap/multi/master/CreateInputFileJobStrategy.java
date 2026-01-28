package com.cyberlogitec.ap_service_gcp.job.strategy.ap.multi.master;

import com.cyberlogitec.ap_service_gcp.dto.DataToWriteDTO;
import com.cyberlogitec.ap_service_gcp.dto.multitrade.CreateInputFileDTO;
import com.cyberlogitec.ap_service_gcp.dto.multitrade.TargetRange;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import com.cyberlogitec.ap_service_gcp.service.helper.DriveServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.helper.GcsService;
import com.cyberlogitec.ap_service_gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import com.cyberlogitec.ap_service_gcp.util.TaskPartitioner;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@Profile({"job-dev", "job-prod"})
@RequiredArgsConstructor
public class CreateInputFileJobStrategy implements JobPlugin {
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;
    private final ObjectMapper objectMapper;
    private final GcsService gcsService;

    @Value("${GOOGLE_ACCOUNT_SERVICE_GROUP_EMAIL}")
    private String accountServiceGroupEmail;

    @Override
    public String getJobName() {
        return "CreateInputFileJobStrategy";
    }

    @Override
    public void execute(JobContext context) throws Exception {
        String totalTasksStr = System.getenv("CLOUD_RUN_TASK_COUNT");
        String currentTaskIndexStr = System.getenv("CLOUD_RUN_TASK_INDEX");
        System.out.println("CreateInputFileJobStrategy");
        CreateInputFileDTO payload = this.objectMapper.convertValue(context.getPayload(), CreateInputFileDTO.class);
        TaskPartitioner.Partition partition = Utilities.getCurrentPartition(totalTasksStr, currentTaskIndexStr, payload.getTotalElement());

        if (partition == null) {
            System.exit(0);
        }
        this.createFoFiles(payload, partition.start, partition.end, context.getJobId() + currentTaskIndexStr);

    }


    private void createFoFiles(
            CreateInputFileDTO createInputFileDTO,
            Integer startRow,
            Integer endRow,
            String taskId


    ) throws IOException {
        ScriptSetting masterScriptSetting = createInputFileDTO.getMasterScriptSetting();
        ScriptSetting file3ScriptSetting = createInputFileDTO.getFile3ScriptSetting();
        int numOfContractOffice = createInputFileDTO.getNumOfContractOffice();
        List<List<Object>> contractOfficesAndLinks = createInputFileDTO.getContractOfficesAndLinks();
        List<List<Object>> contractOfficesAndLinksNoCode = createInputFileDTO.getContractOfficesAndLinksNoCode();
        String inputFileNamePartial = createInputFileDTO.getFile2NamePartial();
        List<String> masterGHQRHQEditors = createInputFileDTO.getMasterGHQRHQEditors();
        masterGHQRHQEditors.add(this.accountServiceGroupEmail);
        List<List<Object>> contractOfficesAndLinksAll = createInputFileDTO.getContractOfficesAndLinksAll();
        List<String> additionalInputFile = createInputFileDTO.getAdditionalInputFile();
        ScriptSetting file2TemplateScriptSetting = createInputFileDTO.getFile2TemplateScriptSetting(); // Cần truyền map này vào
        String file2TemplateId = createInputFileDTO.getFile2TemplateId(); // ID file template
        String file0Id = createInputFileDTO.getFile0Id();
        String file1Id = createInputFileDTO.getFile1Id();
        String file3Id = createInputFileDTO.getFile3Id();
        List<List<Integer>> ae2FirstApLockRanges = createInputFileDTO.getAe2FirstApLockRanges();
        List<List<Integer>> ae2FirstAddApLockRanges = createInputFileDTO.getAe2FirstAddApLockRanges();
        List<List<Integer>> ae2FirstApExpandRanges = createInputFileDTO.getAe2FirstApExpandRanges();
        List<List<Integer>> ae2FirstAddApExpandRanges = createInputFileDTO.getAe2FirstAddApExpandRanges();

        // Data to write sheets
        List<DataToWriteDTO> allDataToWriteDTOs = new ArrayList<>();

        // Load Global Constants/Settings from Map
        String foEditorSheetName = "FO Settings & Contract Office Editors";
        String file2ControlSheetName = "Control"; // Giả định tên
        String file2ApSheetName = "AP";
        String file2AddApSheetName = "Added AP";

        int file2FirstApSheetFirstRow = Integer.parseInt(file2TemplateScriptSetting.getAsString("ae2_AP_DeleteStartRow"));
        int file2FirstAddApSheetFirstRow = Integer.parseInt(file2TemplateScriptSetting.getAsString("ae2_AddedAP_DeleteStartRow"));
        int file2FoFileControlSheetTargetRow = Integer.parseInt(file2TemplateScriptSetting.getAsString("ae2_ControlSheet_TargetRow"));
        int colToExpandAP = Utilities.columnNameToNumber(file2TemplateScriptSetting.getAsString("ae2_AP_ExpandFlag"));
        int colToExpandAddAP = Utilities.columnNameToNumber(file2TemplateScriptSetting.getAsString("ae2_AddAP_ExpandFlag"));

        // link to update in file1/file3
        List<List<Object>> updateLinks = new ArrayList<>();


        // MAIN LOOP
        for (int i = startRow; i <= endRow; i++) {
            int currentIndex = i + 1;
            String officeName = (String) contractOfficesAndLinks.get(i).get(1);
            String folderUrl = (String) contractOfficesAndLinks.get(i).get(2);
            System.out.println("..2.Input AP file [" + currentIndex + " out of " + numOfContractOffice + " / " + officeName + "]: creating...");

            String inputFolderId = Utilities.getIdFromUrl(folderUrl);
            String newFileName = inputFileNamePartial + officeName;
            String file2Id = driveServiceHelper.copyAndMoveFile(file2TemplateId, inputFolderId, newFileName);
            System.out.println("Copy from template finished");
            String file2Url = "https://docs.google.com/spreadsheets/d/" + file2Id;
            updateLinks.add(List.of(file2Url));

            Spreadsheet spreadsheet = this.sheetServiceHelper.getSpreadsheetMetadata(file2Id);
            Map<String, SheetProperties> sheetMap = new HashMap<>();
            for (Sheet s : spreadsheet.getSheets()) {
                sheetMap.put(s.getProperties().getTitle(), s.getProperties());
            }
            // 4. Update Control Sheet (Set FO Name)
            sheetServiceHelper.outputAPI(file2ControlSheetName + "!E4", Collections.singletonList(Collections.singletonList(officeName)), file2Id);

            // 5. Update Local Lists
            contractOfficesAndLinks.get(i).set(4, file2Url);
            contractOfficesAndLinksNoCode.get(i).set(3, file2Url);

            SheetProperties apProps = sheetMap.get(file2ApSheetName);
            SheetProperties addApProps = sheetMap.get(file2AddApSheetName);

            // Japan Special Logic (Unhide columns)
            Map<Integer, List<Map<String, Integer>>> sheetIdToColumnIndices = new HashMap<>();
            if ("Japan".equals(additionalInputFile.get(i)) && "Yes".equals(masterScriptSetting.getAsString("input_TEU_Standard_JP"))) {
                sheetIdToColumnIndices.putAll(Map.of(
                                apProps.getSheetId(), List.of(Map.of("start", colToExpandAP - 1, "end", colToExpandAP)),
                                addApProps.getSheetId(), List.of(Map.of("start", colToExpandAddAP - 1, "end", colToExpandAddAP))
                        )
                );
            }
            // 7. Unhide Columns for BO (Big Office)
            if ("Yes".equals(contractOfficesAndLinksAll.get(i).get(7))) {
                for (List<Integer> range : ae2FirstApExpandRanges) {
                    List<Map<String, Integer>> list = sheetIdToColumnIndices.get(apProps.getSheetId());
                    if (list != null) {
                        list.add(Map.of("start", range.get(0), "end", range.get(1)));
                    } else {
                        sheetIdToColumnIndices.put(apProps.getSheetId(), List.of(Map.of("start", range.get(0), "end", range.get(1))));
                    }
                }
                for (List<Integer> range : ae2FirstAddApExpandRanges) {
                    List<Map<String, Integer>> list = sheetIdToColumnIndices.get(addApProps.getSheetId());
                    if (list != null) {
                        list.add(Map.of("start", range.get(0), "end", range.get(1)));
                    } else {
                        sheetIdToColumnIndices.put(addApProps.getSheetId(), List.of(Map.of("start", range.get(0), "end", range.get(1))));
                    }
                }
            }
            if (!sheetIdToColumnIndices.isEmpty()) {
                this.sheetServiceHelper.unhideSpecificRanges(file2Id, sheetIdToColumnIndices);
            }
            // 6. PROTECTIONS (Locking)
            List<TargetRange> targetRangesToProtect = new ArrayList<>();
            // Control
            targetRangesToProtect.add(createRange(sheetMap.get(file2ControlSheetName), 0, 7, 1, null)); // null = max col
            targetRangesToProtect.add(createRange(sheetMap.get(file2ControlSheetName), 14, file2FoFileControlSheetTargetRow, 1, null));
            // AP Sheet
            targetRangesToProtect.add(createRange(apProps, 0, file2FirstApSheetFirstRow - 1, 0, null)); // Header
            for (List<Integer> range : ae2FirstApLockRanges) {
                targetRangesToProtect.add(createRange(apProps, 0, null, range.get(0), range.get(1)));
            }
            // Added AP Sheet
            targetRangesToProtect.add(createRange(addApProps, 0, file2FirstAddApSheetFirstRow - 1, 0, null));
            for (List<Integer> range : ae2FirstAddApLockRanges) {
                targetRangesToProtect.add(createRange(addApProps, 0, null, range.get(0), range.get(1)));
            }
            // Lock non-whitelisted sheets
            String unlockedSheetsStr = masterScriptSetting.getAsString("ae2_unlockedSheets");
            List<String> unlockedList = Arrays.asList(unlockedSheetsStr.split(","));
            for (String sheetName : sheetMap.keySet()) {
                if (!unlockedList.contains(sheetName)) {
                    targetRangesToProtect.add(createFullSheetRange(sheetMap.get(sheetName)));
                    targetRangesToProtect.add(createFullSheetRange(sheetMap.get(sheetName)));
                }
            }
            // Apply Protections
            sheetServiceHelper.batchAddProtectionRange(file2Id, targetRangesToProtect, masterGHQRHQEditors);
            System.out.println("....2.Input AP file [" + currentIndex + "] created");

        }

        // FINAL UPDATES
        List<List<Object>> updateArray = contractOfficesAndLinksNoCode.subList(startRow, endRow + 1);
        allDataToWriteDTOs.add(new DataToWriteDTO(file0Id, String.format("'%s'!A%d:D%d", foEditorSheetName, 2 + startRow, endRow + 2), updateArray));

        // Update ae1
        String ae1DistLinkRange = masterScriptSetting.getAsString("master_ae1_DistributeFileLink");
        allDataToWriteDTOs.add(new DataToWriteDTO(file1Id, Utilities.calculateSubRangeA1(ae1DistLinkRange, startRow, endRow + 1), updateLinks));

        String ae3ConsolidateCheckSheetName = "Consolidate Check";
        String ae3FileLinkColumn = file3ScriptSetting.getAsString("ae3_ConsolidateCheck_LinksOfFile");
        int ae3ConsolidateCheckStartRow = Integer.parseInt(file3ScriptSetting.getAsString("ae3_ConsolidateCheck_StartRow"));

        String ae3Range = Utilities.calculateSubRangeA1(String.format("'%s'!%s%d:%s%d",
                ae3ConsolidateCheckSheetName,
                ae3FileLinkColumn, ae3ConsolidateCheckStartRow,
                ae3FileLinkColumn, updateLinks.size() + ae3ConsolidateCheckStartRow - 1), startRow, endRow + 1);

        allDataToWriteDTOs.add(new DataToWriteDTO(file3Id, ae3Range, updateLinks));
        this.gcsService.uploadStreaming(taskId, allDataToWriteDTOs);
        System.out.println("Create Fo Files Finished");
    }

    // --- Helper Methods & Inner Classes ---
    private TargetRange createRange(SheetProperties props, Integer startRow, Integer endRow, Integer startCol, Integer endCol) {
        //If null then get max
        int maxRow = props.getGridProperties().getRowCount();
        int maxCol = props.getGridProperties().getColumnCount();
        return new TargetRange(
                props.getSheetId(),
                startRow != null ? startRow : 0,
                endRow != null ? endRow : maxRow,
                startCol != null ? startCol : 0,
                endCol != null ? endCol : maxCol
        );
    }

    private TargetRange createFullSheetRange(SheetProperties props) {
        return createRange(
                props,
                null,
                null,
                null,
                null
        );
    }
}
