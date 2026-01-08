package com.cyberlogitec.ap_service_gcp.job.extension;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Profile("job")
public class JobRunner {

    private final JobRegistry registry;

    public void run(String jobName, JobContext context) throws Exception {
        registry.getPlugin(jobName).execute(context);
    }
}