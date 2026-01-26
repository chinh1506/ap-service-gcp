package com.cyberlogitec.ap_service_gcp.job;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobRunner;
import com.cyberlogitec.ap_service_gcp.service.helper.GcsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"job-dev","job-prod"})
public class JobApplication implements CommandLineRunner {

    private final JobRunner runner;
    private final ObjectMapper mapper;
    private final GcsService gcsService;

    public JobApplication(JobRunner runner, ObjectMapper mapper, GcsService gcsService) {
        this.runner = runner;
        this.mapper = mapper;
        this.gcsService = gcsService;
    }

    @Override
    public void run(String... args) throws Exception {

        String jobName = System.getenv("JOB_NAME");
        String jobPayloadId = System.getenv("JOB_PAYLOAD_ID");
        byte[] bytes = this.gcsService.getFile(jobPayloadId);
        JobContext context = mapper.readValue(bytes, JobContext.class);

        runner.run(jobName, context);
        System.exit(0);
    }

}
