package com.cyberlogitec.ap_service_gcp.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateSpec {
    public List<Container> containers;
    public Integer maxRetries;
    public String timeoutSeconds;
    public String serviceAccountName;
}
