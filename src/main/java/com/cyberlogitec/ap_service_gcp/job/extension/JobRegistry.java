package com.cyberlogitec.ap_service_gcp.job.extension;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("job")
public class JobRegistry {

    private final Map<String, JobPlugin> jobMap;

    public JobRegistry(List<JobPlugin> plugins) {
        this.jobMap = plugins.stream()
                .collect(Collectors.toMap(
                        JobPlugin::getJobName,
                        Function.identity()
                ));
    }

    public JobPlugin getPlugin(String jobName) {
        JobPlugin plugin = jobMap.get(jobName);
        if (plugin == null) {
            throw new IllegalArgumentException("Unknown job: " + jobName);
        }
        return plugin;
    }
}