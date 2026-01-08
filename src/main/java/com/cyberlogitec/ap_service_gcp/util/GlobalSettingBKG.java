package com.cyberlogitec.ap_service_gcp.util;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class GlobalSettingBKG {
    private String workFileId;
    private ScriptSetting scriptSettingsPart1;
    private ScriptSetting scriptSettingsPart2;
}
