package com.cyberlogitec.ap.service.gcp.job.extension;

public interface JobPlugin {

    /**
     * Unique job name (string)
     */
    String getJobName();

    /**
     * handle job
     */
     void execute(JobContext<Object> context) throws Exception;
}

