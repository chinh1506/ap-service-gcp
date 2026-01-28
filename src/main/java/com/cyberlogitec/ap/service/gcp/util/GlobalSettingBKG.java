package com.cyberlogitec.ap.service.gcp.util;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GlobalSettingBKG {
    private String workFileId;
    private ScriptSetting scriptSettingsPart1;
    private ScriptSetting scriptSettingsPart2;
}
