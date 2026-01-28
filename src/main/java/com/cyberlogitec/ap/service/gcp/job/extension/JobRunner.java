package com.cyberlogitec.ap.service.gcp.job.extension;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Profile({"job-dev","job-prod"})
public class JobRunner {

    private final JobRegistry registry;

    public void run(String jobName, JobContext<Object> context) throws Exception {
            registry.getPlugin(jobName).execute(context);
    }

}