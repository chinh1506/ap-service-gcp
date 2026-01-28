package com.cyberlogitec.ap.service.gcp.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Condition {
    public String type;
    public String status;
    public String message;
    public String lastTransitionTime;
    public String reason;
    public String severity;
}
