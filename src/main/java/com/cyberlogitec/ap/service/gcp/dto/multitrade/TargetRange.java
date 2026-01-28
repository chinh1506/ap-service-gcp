package com.cyberlogitec.ap.service.gcp.dto.multitrade;

import lombok.Data;

@Data
public class TargetRange {
    Integer sheetId;
    Integer startRow, endRow, startCol, endCol;
    public TargetRange(Integer sheetId, Integer r1, Integer r2, Integer c1, Integer c2) {
        this.sheetId = sheetId; this.startRow = r1; this.endRow = r2; this.startCol = c1; this.endCol = c2;
    }
}
