package com.cyberlogitec.ap.service.gcp.job.extension;

import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobContext<T> {
    private WorkflowState workflowState; // this will save in database
    private T payload; // this will save into GCS
    private int taskCount = 1;
}
