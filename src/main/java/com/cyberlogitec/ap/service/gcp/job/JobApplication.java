package com.cyberlogitec.ap.service.gcp.job;

import com.cyberlogitec.ap.service.gcp.job.extension.JobContext;
import com.cyberlogitec.ap.service.gcp.job.extension.JobRunner;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.cyberlogitec.ap.service.gcp.repository.WorkflowStateRepository;
import com.cyberlogitec.ap.service.gcp.service.CloudRunJobService;
import com.cyberlogitec.ap.service.gcp.service.helper.GcsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"job-dev", "job-prod"})
@RequiredArgsConstructor
public class JobApplication implements CommandLineRunner {

    private final JobRunner runner;
    private final ObjectMapper mapper;
    private final GcsService gcsService;
    private final WorkflowStateRepository workflowStateRepository;

    @Override
    public void run(String... args) throws Exception {
        String workflowId = System.getenv(CloudRunJobService.WORKFLOW_ID_ENV_KEY);
        WorkflowState workflow = workflowStateRepository.get(workflowId);
        byte[] bytes = this.gcsService.getFile(workflow.getCurrentStepDataKey());
        JobContext<Object> context = mapper.readValue(bytes, new TypeReference<JobContext<Object>>() {
        });
        runner.run(workflow.getCurrentStepName(), context);
        System.exit(0);
    }

}
