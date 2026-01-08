package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.run.v2.EnvVar;
import com.google.cloud.run.v2.JobName;
import com.google.cloud.run.v2.JobsClient;
import com.google.cloud.run.v2.RunJobRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CloudRunJobService {
    private final ObjectMapper objectMapper;

    public CloudRunJobService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void runCloudRunJob(String jobName, JobContext context) throws IOException {

        String projectId = System.getenv("GOOGLE_CLOUD_PROJECT_ID");
        String region = System.getenv("GOOGLE_CLOUD_REGION");
        String cloudRunJobName = System.getenv("CLOUD_RUN_JOB_NAME");

        try (JobsClient jobsClient = JobsClient.create()) {
            // ENV VARS
            EnvVar jobNameEnv = EnvVar.newBuilder()
                    .setName("JOB_NAME")
                    .setValue(jobName)
                    .build();

            EnvVar payloadEnv = EnvVar.newBuilder()
                    .setName("JOB_PAYLOAD")
                    .setValue(objectMapper.writeValueAsString(context))
                    .build();

            RunJobRequest.Overrides.ContainerOverride containerOverride =
                    RunJobRequest.Overrides.ContainerOverride.newBuilder()
                            .addEnv(jobNameEnv)
                            .addEnv(payloadEnv)
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
            jobsClient.runJobAsync(request);
        }
    }
}
