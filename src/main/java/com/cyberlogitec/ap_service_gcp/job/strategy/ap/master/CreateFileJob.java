package com.cyberlogitec.ap_service_gcp.job.strategy.ap.master;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"job-dev","job-prod"})
public class CreateFileJob implements JobPlugin {

    @Override
    public String getJobName() {
        return "CreateFileJob";
    }

    @Override
    public void execute(JobContext context) throws Exception {

    }
}
