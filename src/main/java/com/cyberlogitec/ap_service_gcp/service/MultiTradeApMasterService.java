package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.dto.multitrade.TargetRange;
import com.cyberlogitec.ap_service_gcp.service.helper.DriveServiceHelper;
import com.cyberlogitec.ap_service_gcp.service.helper.SheetServiceHelper;
import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MultiTradeApMasterService {
    private final SheetServiceHelper sheetServiceHelper;
    private final DriveServiceHelper driveServiceHelper;

    public void createFoFiles(
            ScriptSetting masterScriptSetting,
            ScriptSetting file3ScriptSetting,
            int startRow,
            int numOfContractOffice,
            List<List<Object>> contractOfficesAndLinks,
            List<List<Object>> contractOfficesAndLinksNoCode,
            String inputFileNamePartial,
            List<String> masterGHQRHQEditors,
            List<List<Object>> contractOfficesAndLinksAll,
            List<String> additionalInputFile,
            Map<String, String> inputFileTemplateSettingsMap, // Cần truyền map này vào
            String inputFileTemplateId, // ID file template
            String file0Id,
            String file1Id,
            String file2Id,
            List<List<Integer>> ae2FirstApLockRanges,
            List<List<Integer>> ae2FirstAddApLockRanges,
            List<List<Integer>> ae2FirstApExpandRanges,
            List<List<Integer>> ae2FirstAddApExpandRanges,
            String foEditorSheetName

    ) throws IOException {

        // Load Global Constants/Settings from Map
        String file2ControlSheetName = "Control"; // Giả định tên
        String file2ScriptSettingsSheetName = "Script Settings";
        String file2BsaSheetName = "BSA";
        String file2ApSheetName = "AP";
        String file2MergedApSheetName = "Merged AP";
        String file2GroupRfaSheetName = "Group RFA";
        String file2AddApSheetName = "Added AP";

        // Lấy các setting từ map
        int file2FirstApSheetFirstRow = Integer.parseInt(inputFileTemplateSettingsMap.get("ae2_AP_DeleteStartRow"));
        int file2FirstAddApSheetFirstRow = Integer.parseInt(inputFileTemplateSettingsMap.get("ae2_AddedAP_DeleteStartRow"));
        int file2FoFileControlSheetTargetRow = Integer.parseInt(inputFileTemplateSettingsMap.get("ae2_ControlSheet_TargetRow"));
        String colToExpandAP = inputFileTemplateSettingsMap.get("ae2_AP_ExpandFlag") + "1";
        String colToExpandAddAP = inputFileTemplateSettingsMap.get("ae2_AddAP_ExpandFlag") + "1";

        // MAIN LOOP
        for (int i = startRow; i < numOfContractOffice; i++) {
            int currentIndex = i + 1;
            String officeName = (String) contractOfficesAndLinks.get(i).get(1);
            String folderUrl = (String) contractOfficesAndLinks.get(i).get(2);

            System.out.println("..2.Input AP file [" + currentIndex + " out of " + numOfContractOffice + " / " + officeName + "]: creating...");

            // 1. Get Destination Folder ID
            String desFolderId = Utilities.getIdFromUrl(folderUrl);

            // 2. Copy File
            String newFileName = inputFileNamePartial + officeName;
            String ae2FoFileId = driveServiceHelper.copyAndMoveFile(inputFileTemplateId, desFolderId, newFileName);
            String ae2FoFileUrl = "https://docs.google.com/spreadsheets/d/" + ae2FoFileId;

            // 3. Get Sheet Metadata (ID, Title, GridProperties)
            Spreadsheet spreadsheet = this.sheetServiceHelper.getSpreadsheetMetadata(ae2FoFileId);
            Map<String, SheetProperties> sheetMap = new HashMap<>();
            for (Sheet s : spreadsheet.getSheets()) {
                sheetMap.put(s.getProperties().getTitle(), s.getProperties());
            }

            // 4. Update Control Sheet (Set FO Name)
            sheetServiceHelper.outputAPI(file2ControlSheetName + "!E4", Collections.singletonList(Collections.singletonList(officeName)), ae2FoFileId);

            // 5. Update Local Lists
            contractOfficesAndLinks.get(i).set(4, ae2FoFileUrl);
            contractOfficesAndLinksNoCode.get(i).set(3, ae2FoFileUrl);

            // 6. PROTECTIONS (Locking)
            sheetServiceHelper.removeAllProtections(spreadsheet);

            List<TargetRange> targetRangesToProtect = new ArrayList<>();

            // Helper lambda/method để add range nhanh
            // Control
            targetRangesToProtect.add(createRange(sheetMap.get(file2ControlSheetName), 0, 7, 1, null)); // null = max col
            targetRangesToProtect.add(createRange(sheetMap.get(file2ControlSheetName), 14, file2FoFileControlSheetTargetRow, 1, null));

            // Script Settings, BSA, Merged AP, Group RFA (Lock All)
            targetRangesToProtect.add(createFullSheetRange(sheetMap.get(file2ScriptSettingsSheetName)));
            targetRangesToProtect.add(createFullSheetRange(sheetMap.get(file2BsaSheetName)));
            targetRangesToProtect.add(createFullSheetRange(sheetMap.get(file2MergedApSheetName)));
            targetRangesToProtect.add(createFullSheetRange(sheetMap.get(file2GroupRfaSheetName)));

            // AP Sheet
            SheetProperties apProps = sheetMap.get(file2ApSheetName);
            targetRangesToProtect.add(createRange(apProps, 0, file2FirstApSheetFirstRow - 1, 0, null)); // Header
            for (List<Integer> range : ae2FirstApLockRanges) {
                targetRangesToProtect.add(createRange(apProps, 0, null, range.get(0), range.get(1)));
            }

            // Japan Special Logic (Unhide columns)
            if ("Japan".equals(additionalInputFile.get(i)) && "Yes".equals(masterScriptSetting.getAsString("input_TEU_Standard_JP"))) {
//                    sheetServiceHelper.unhideColumn(ae2FoFileId, apProps.getSheetId(), colToExpandAP);
//                    sheetServiceHelper.unhideColumn(ae2FoFileId, sheetMap.get(ae2AddApSheetName).getSheetId(), colToExpandAddAP);
            }

            // Added AP Sheet
            SheetProperties addApProps = sheetMap.get(file2AddApSheetName);
            targetRangesToProtect.add(createRange(addApProps, 0, file2FirstAddApSheetFirstRow - 1, 0, null));
            for (List<Integer> range : ae2FirstAddApLockRanges) {
                targetRangesToProtect.add(createRange(addApProps, 0, null, range.get(0), range.get(1)));
            }

            // Lock non-whitelisted sheets
            String unlockedSheetsStr = masterScriptSetting.getAsString("ae2_unlockedSheets");
            List<String> unlockedList = Arrays.asList(unlockedSheetsStr.split(","));
            for (String sheetName : sheetMap.keySet()) {
                if (!unlockedList.contains(sheetName)) {
                    // Nếu chưa được add ở trên thì add full sheet
                    // Logic này cần xử lý cẩn thận để tránh duplicate protection,
                    // nhưng về cơ bản là add protection cho sheetId
                    targetRangesToProtect.add(createFullSheetRange(sheetMap.get(sheetName)));
                }
            }

            // Apply Protections
            try {
                sheetServiceHelper.batchAddProtectionRange(ae2FoFileId, targetRangesToProtect, masterGHQRHQEditors);
            } catch (Exception e) {
                if (e.getMessage().contains("already has sheet protection")) {
                    sheetServiceHelper.removeAllProtections(spreadsheet);
                    sheetServiceHelper.batchAddProtectionRange(ae2FoFileId, targetRangesToProtect, masterGHQRHQEditors);
                }
            }

            // 7. Unhide Columns for BO (Business Office?)
            if ("Yes".equals(contractOfficesAndLinksAll.get(i).get(7))) {
                for (List<Integer> range : ae2FirstApExpandRanges) {
//                        sheetServiceHelper.unhideColumnsByIndex(ae2FoFileId, apProps.getSheetId(), range[0], range[1]);
                }
                for (List<Integer> range : ae2FirstAddApExpandRanges) {
//                        sheetServiceHelper.unhideColumnsByIndex(ae2FoFileId, addApProps.getSheetId(), range[0], range[1]);
                }
            }

            System.out.println("....2.Input AP file [" + currentIndex + "] created");
            List<List<Object>> updateArray = contractOfficesAndLinksNoCode.subList(startRow, numOfContractOffice);
            String range = String.format("%s!A%d:D%d",
                    foEditorSheetName,
                    2 + startRow,
                    numOfContractOffice - startRow + 1 + startRow);

            sheetServiceHelper.outputAPI(range, updateArray, file0Id);
        } // End Loop

        // FINAL UPDATES
        int endRowIndex = numOfContractOffice - startRow + 1 + startRow;
        List<List<Object>> updateArray = contractOfficesAndLinksNoCode.subList(startRow, numOfContractOffice);


        // Update ae0
        sheetServiceHelper.outputAPI(
                String.format("%s!A%d:D%d", foEditorSheetName, 2 + startRow, endRowIndex),
                updateArray,
                file0Id
        );

        // Get Links for ae1/ae3 update
        List<List<Object>> updateLinks = sheetServiceHelper.inputAPI(file0Id, String.format("'%s'!D2:D%d", foEditorSheetName, numOfContractOffice + 1));

        // Update ae1
        String ae1DistLink = masterScriptSetting.getAsString("master_ae1_DistributeFileLink");
        sheetServiceHelper.outputAPI(ae1DistLink, updateLinks, file1Id);

        // Update ae3
        String ae3ConsolidateCheckSheetName = "Consolidate Check";
        // Giả sử ae3SettingsMap được load từ gs hoặc truyền vào

        String ae3FileLinkColumn = file3ScriptSetting.getAsString("ae3_ConsolidateCheck_LinksOfFile");
        int ae3ConsolidateCheckStartRow = Integer.parseInt(file3ScriptSetting.getAsString("ae3_ConsolidateCheck_StartRow"));

        String ae3Range = String.format("%s!%s%d:%s%d",
                ae3ConsolidateCheckSheetName,
                ae3FileLinkColumn, ae3ConsolidateCheckStartRow,
                ae3FileLinkColumn, updateLinks.size() + ae3ConsolidateCheckStartRow - 1);

        sheetServiceHelper.outputAPI(ae3Range, updateLinks, file2Id);

        System.out.println("Create Fo Files Finished");
    }

    // --- Helper Methods & Inner Classes ---

    private TargetRange createRange(SheetProperties props, Integer startRow, Integer endRow, Integer startCol, Integer endCol) {
        // Nếu null thì lấy max
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
        return createRange(props, null, null, null, null);
    }


    // Interface cho Timer
    public interface ButtonTimer {
        void checkTimerStatus();

        boolean isTimeOver();

        void pause(Map<String, Object> state);
    }
}
