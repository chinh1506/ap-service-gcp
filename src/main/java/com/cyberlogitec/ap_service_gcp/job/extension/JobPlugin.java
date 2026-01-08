package com.cyberlogitec.ap_service_gcp.job.extension;

public interface JobPlugin {

    /**
     * Unique job name (string)
     */
    String getJobName();

    /**
     * handle job
     */
     void execute(JobContext context) throws Exception;
}

