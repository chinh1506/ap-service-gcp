package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.model.JobCache;
import com.cyberlogitec.ap_service_gcp.service.helper.GcsService;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.run.v2.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class CloudRunJobService {
    @Value("${GOOGLE_CLOUD_PROJECT_ID}")
    private String projectId;
    @Value("${GOOGLE_CLOUD_REGION}")
    private String region;
    @Value("${CLOUD_RUN_JOB_NAME}")
    private String cloudRunJobName;
    private final Firestore firestore;
    private final GcsService gcsService;
    private final ObjectMapper objectMapper;


    public void runJob(String jobName, JobContext context) {
        // 3. Vẫn bắt buộc phải set Endpoint
        JobsSettings.Builder settingsBuilder = JobsSettings.newHttpJsonBuilder();
        String endpoint = region + "-run.googleapis.com:443";
        settingsBuilder.setEndpoint(endpoint);

        try (JobsClient jobsClient = JobsClient.create(settingsBuilder.build())) {
            this.gcsService.uploadStreaming(context.getJobId(), context);
            Utilities.logMemory("after write as String");
            // ENV VARS
            EnvVar jobNameEnv = EnvVar.newBuilder()
                    .setName("JOB_NAME")
                    .setValue(jobName)
                    .build();

            EnvVar payloadIdEnv = EnvVar.newBuilder()
                    .setName("JOB_PAYLOAD_ID")
                    .setValue(context.getJobId())
                    .build();

            RunJobRequest.Overrides.ContainerOverride containerOverride =
                    RunJobRequest.Overrides.ContainerOverride.newBuilder()
                            .addEnv(jobNameEnv)
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
            // async trigger
            OperationFuture<Execution, Execution> operation = jobsClient.runJobAsync(request);
            Execution execution = operation.getMetadata().get();

            String[] splitName = execution.getName().split("/");
            String executionName = splitName[splitName.length - 1];
            this.setJobCache(new JobCache(executionName, context.getJobId(), jobName));

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

    public void setJobCache(JobCache jobCache) throws ExecutionException, InterruptedException {
        this.firestore.collection("job_collection").document(jobCache.getExecutionName()).set(jobCache).get();
    }

    public JobCache getJobValue(String executionName) throws ExecutionException, InterruptedException {
        return this.firestore.collection("job_collection").document(executionName).get().get().toObject(JobCache.class);
    }

    public void deleteJobCache(String executionName) {
        this.firestore.collection("job_collection").document(executionName).delete();
    }
}
