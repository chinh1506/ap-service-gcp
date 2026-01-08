package com.cyberlogitec.ap_service_gcp.job;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("job")
public class JobApplication implements CommandLineRunner {

    private final JobRunner runner;
    private final ObjectMapper mapper;

    public JobApplication(JobRunner runner, ObjectMapper mapper) {
        this.runner = runner;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws Exception {

        String jobName = System.getenv("JOB_NAME");
        String payloadJson = System.getenv("JOB_PAYLOAD");

        JobContext context = mapper.readValue(payloadJson, JobContext.class);

        runner.run(jobName, context);
        System.exit(0);
    }

//    public static void main(String[] args) {
//        SpringApplication app = new SpringApplication(JobApplication.class);
//        app.setAdditionalProfiles("job");
//        app.run(args);
//    }
}
