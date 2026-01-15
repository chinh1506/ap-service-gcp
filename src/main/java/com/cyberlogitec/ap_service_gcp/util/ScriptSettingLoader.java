package com.cyberlogitec.ap_service_gcp.util;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class ScriptSettingLoader {
    private final Sheets sheetsService;
    private final String DEFAULT_SHEET_NAME = "Script Settings";
    private final String DEFAULT_RANGE = "F1:G";

    public ScriptSettingLoader(Sheets sheetsService) {
        this.sheetsService = sheetsService;
    }

    public ScriptSetting getSettingsMap(String ssaId) throws IOException {
        return getSettingsMap(ssaId, DEFAULT_SHEET_NAME, DEFAULT_RANGE);
    }

    public ScriptSetting getSettingsMap(String ssaId, String sheetName) throws IOException {
        return getSettingsMap(ssaId, sheetName, DEFAULT_RANGE);
    }

    public ScriptSetting getSettingsMap(String ssaId, String sheetName, String a1Range) throws IOException {
        // Xây dựng range: 'Script Settings'!F1:G
        String range = "'" + sheetName + "'!" + a1Range;

        // Gọi API lấy dữ liệu
        ValueRange response = sheetsService.spreadsheets().values()
                .get(ssaId, range)
                .execute();

        List<List<Object>> values = response.getValues();
        Map<String, Object> map = new HashMap<>();
        if (values == null || values.isEmpty()) {
            return new ScriptSetting(map);
        }
        map = values.stream()
                .filter(row -> row != null && !row.isEmpty())
                .collect(Collectors.toMap(
                        row -> row.get(0).toString(),                 // Key
                        row -> (row.size() > 1) ? row.get(1).toString() : "", // Value
                        (existing, replacement) -> replacement
                ));


        return new ScriptSetting(map);
    }
}
