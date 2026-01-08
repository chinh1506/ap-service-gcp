package com.cyberlogitec.ap_service_gcp.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ScriptSetting {
    private Map<String, Object> settings;

    public String getAsString(final String key) {
        return (String) settings.get(key);
    }

    public int getAsInt(final String key) {
        return  Integer.parseInt((String) settings.get(key));
    }

    public boolean getAsBoolean(final String key) {
        return Boolean.parseBoolean((String) settings.get(key));
    }

    public double getAsDouble(final String key) {
        return Double.parseDouble((String) settings.get(key));
    }

}
