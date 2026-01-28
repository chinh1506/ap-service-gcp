package com.cyberlogitec.ap.service.gcp.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PubSubMessage {
    public ProtoPayload protoPayload;
    public String insertId;
    public Resource resource;
    public String timestamp;
    public String severity;
    public Map<String, String> labels;
    public String logName;
    public String receiveTimestamp;
}

