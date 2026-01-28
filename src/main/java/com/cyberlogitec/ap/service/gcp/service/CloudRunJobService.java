package com.cyberlogitec.ap.service.gcp.service;

import com.cyberlogitec.ap.service.gcp.job.extension.JobContext;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.cyberlogitec.ap.service.gcp.repository.WorkflowStateRepository;
import com.cyberlogitec.ap.service.gcp.service.helper.GcsService;
import com.cyberlogitec.ap.service.gcp.util.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.run.v2.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class CloudRunJobService {
    @Value("${GOOGLE_CLOUD_PROJECT_ID}")
    private String projectId;
    @Value("${GOOGLE_CLOUD_REGION}")
    private String region;
    @Value("${CLOUD_RUN_JOB_NAME}")
    private String cloudRunJobName;
    private final WorkflowStateRepository workflowStateRepository;
    private final GcsService gcsService;
    private final ObjectMapper objectMapper;

    public static final String WORKFLOW_ID_ENV_KEY="WORKFLOW_ID";


    public void runJob(JobContext<?> context) {
        JobsSettings.Builder settingsBuilder = JobsSettings.newHttpJsonBuilder();
        String endpoint = region + "-run.googleapis.com:443";
        settingsBuilder.setEndpoint(endpoint);

        WorkflowState workflowState= context.getWorkflowState();

        try (JobsClient jobsClient = JobsClient.create(settingsBuilder.build())) {
            this.gcsService.uploadStreaming(workflowState.getCurrentStepDataKey(), context);
            Utilities.logMemory("after write as String");
            EnvVar payloadIdEnv = EnvVar.newBuilder()
                    .setName(WORKFLOW_ID_ENV_KEY)
                    .setValue(workflowState.getWorkflowId())
                    .build();

            RunJobRequest.Overrides.ContainerOverride containerOverride =
                    RunJobRequest.Overrides.ContainerOverride.newBuilder()
                            .addEnv(payloadIdEnv)
                            .build();

            RunJobRequest request =
                    RunJobRequest.newBuilder()
                            .setName(
                                    JobName.of(projectId, region, cloudRunJobName).toString()
                            )
                            .setOverrides(
                                    RunJobRequest.Overrides.newBuilder()
                                            .setTaskCount(context.getTaskCount())
                                            .addContainerOverrides(containerOverride)
                                            .build()
                            )
                            .build();
            OperationFuture<Execution, Execution> operation = jobsClient.runJobAsync(request);
            Execution execution = operation.getMetadata().get();

            String[] splitName = execution.getName().split("/");
            String executionName = splitName[splitName.length - 1];
            workflowState.setExecutionName(executionName);
            this.workflowStateRepository.save(workflowState);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void getJobExecutionDetails(String executionId) throws IOException {
        try (ExecutionsClient executionsClient = ExecutionsClient.create()) {
            String executionName = ExecutionName.of(projectId, region, cloudRunJobName, executionId).toString();
            System.out.println("Đang kiểm tra Execution: " + executionName);
            Execution execution = executionsClient.getExecution(executionName);

            int succeededCount = execution.getSucceededCount();
            int failedCount = execution.getFailedCount();
            int runningCount = execution.getRunningCount();
            int cancelledCount = execution.getCancelledCount();

            System.out.println("--- KẾT QUẢ CHI TIẾT ---");
            System.out.println("Thành công: " + succeededCount);
            System.out.println("Thất bại:   " + failedCount);
            System.out.println("Đang chạy:  " + runningCount);
            System.out.println("Đã hủy:     " + cancelledCount);

        }
    }

}
