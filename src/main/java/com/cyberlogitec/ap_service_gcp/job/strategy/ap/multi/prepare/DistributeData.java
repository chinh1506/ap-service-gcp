package com.cyberlogitec.ap_service_gcp.job.strategy.ap.multi.prepare;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"job-dev","job-prod"})
public class DistributeData implements JobPlugin {
    @Override
    public String getJobName() {
        return "DistributeData";
    }

    @Override
    public void execute(JobContext context) throws Exception {
        System.out.println("DistributeData");
    }
}
