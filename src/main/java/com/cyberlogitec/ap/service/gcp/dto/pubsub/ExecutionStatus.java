package com.cyberlogitec.ap.service.gcp.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionStatus {
    public Integer observedGeneration;
    public List<Condition> conditions;
    public String startTime;
    public String completionTime;
    public Integer succeededCount;
    public Integer failedCount;
    public String logUri;
}
