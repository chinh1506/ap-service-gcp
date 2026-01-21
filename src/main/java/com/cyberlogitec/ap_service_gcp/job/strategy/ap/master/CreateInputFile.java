package com.cyberlogitec.ap_service_gcp.job.strategy.ap.master;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import org.springframework.stereotype.Component;

@Component
public class CreateInputFile implements JobPlugin {
    @Override
    public String getJobName() {
        return "CreateInputFile";
    }

    @Override
    public void execute(JobContext context) throws Exception {

    }

    private void createInputFile(){

    }


}
