package com.cyberlogitec.ap.service.gcp.model;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkflowState {
    @DocumentId
    private String workflowId;
    private String executionName;
    private String currentStepName;
    private Integer currentStepNumber;
    private Integer totalSteps;
    private WorkflowStatus status;
    @Builder.Default
    private List<String> fileNamesToDelete = new ArrayList<>();// all files will be deleted when this workflow had been finished

    public String getCurrentStepDataKey() {
        return "Flow_" + workflowId + "-Step_" + currentStepName;
    }

    public static WorkflowState startWorkflow(String currentStepName,Integer totalSteps){
        return WorkflowState.builder()
                .workflowId(UUID.randomUUID().toString())
                .currentStepName(currentStepName)
                .currentStepNumber(1)
                .totalSteps(totalSteps)
                .status(WorkflowStatus.RUNNING)
                .build();
    }


}
