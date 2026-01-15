package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.run.v2.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class CloudRunJobService {
    private final GcsService gcsService;
    private final String projectId = System.getenv("GOOGLE_CLOUD_PROJECT_ID");
    private final String region = System.getenv("GOOGLE_CLOUD_REGION");
    private final String cloudRunJobName = System.getenv("CLOUD_RUN_JOB_NAME");
    private final Firestore firestore;

    public CloudRunJobService(GcsService gcsService, Firestore firestore) {
        this.gcsService = gcsService;
        this.firestore = firestore;
    }

    public void runCloudRunJob(String jobName, JobContext context) throws IOException {
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
//            this.firestore.collection("job_collection").document("job").set(Map.of(executionName,context.getJobId()));
            this.setJobCache(executionName, context.getJobId());

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

            // 4. In kết quả hoặc xử lý logic
            System.out.println("--- KẾT QUẢ CHI TIẾT ---");
            System.out.println("✅ Thành công: " + succeededCount);
            System.out.println("❌ Thất bại:   " + failedCount);
            System.out.println("🏃 Đang chạy:  " + runningCount);
            System.out.println("🚫 Đã hủy:     " + cancelledCount);

        }
    }

    public void setJobCache(String key, String value) throws ExecutionException, InterruptedException {
        this.firestore.collection("job_collection").document("job").set(Map.of(key, value)).get();
    }
    public String getJobValue(String key) throws ExecutionException, InterruptedException {
        return this.firestore.collection("job_collection").document("job").get().get().getString(key);
    }
}
