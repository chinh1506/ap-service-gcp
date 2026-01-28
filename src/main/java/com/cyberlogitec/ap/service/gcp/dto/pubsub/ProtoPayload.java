package com.cyberlogitec.ap.service.gcp.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProtoPayload {
    @JsonProperty("@type")
    public String type;
    public StatusInfo status;
    public String serviceName;
    public String methodName;
    public String resourceName;
    public ExecutionResponse response;
}
