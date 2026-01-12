package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.run.v2.*;
import com.google.longrunning.Operation;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

@Service
public class CloudRunJobService {
    private final ObjectMapper objectMapper;
    private final GcsService gcsService;

    public CloudRunJobService(ObjectMapper objectMapper, GcsService gcsService) {
        this.objectMapper = objectMapper;
        this.gcsService = gcsService;
    }

    public void runCloudRunJob(String jobName, JobContext context) throws IOException {

        String projectId = System.getenv("GOOGLE_CLOUD_PROJECT_ID");
        String region = System.getenv("GOOGLE_CLOUD_REGION");
        String cloudRunJobName = System.getenv("CLOUD_RUN_JOB_NAME");

        // 3. Vẫn bắt buộc phải set Endpoint
        JobsSettings.Builder settingsBuilder = JobsSettings.newHttpJsonBuilder();
        String endpoint = region + "-run.googleapis.com:443";
        settingsBuilder.setEndpoint(endpoint);

        try (JobsClient jobsClient = JobsClient.create(settingsBuilder.build())) {
            this.gcsService.upload(context.getTaskId(), objectMapper.writeValueAsString(context).getBytes(StandardCharsets.UTF_8));

            // ENV VARS
            EnvVar jobNameEnv = EnvVar.newBuilder()
                    .setName("JOB_NAME")
                    .setValue(jobName)
                    .build();

            EnvVar payloadIdEnv = EnvVar.newBuilder()
                    .setName("JOB_PAYLOAD_ID")
                    .setValue(context.getTaskId())
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

            OperationFuture<Execution, Execution> future = jobsClient.runJobAsync(request);
            Execution execution = future.get();
            int succeededCount = execution.getSucceededCount();

            if (succeededCount == context.getTaskCount()) {
                System.out.println("Job " + jobName + " has completed successfully");
                this.gcsService.deleteFile(context.getTaskId());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
