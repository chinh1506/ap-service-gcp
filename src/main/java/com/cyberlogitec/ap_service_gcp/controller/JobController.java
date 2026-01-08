package com.cyberlogitec.ap_service_gcp.controller;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobRunner;
import com.cyberlogitec.ap_service_gcp.service.CloudRunJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@AllArgsConstructor
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);
    private final CloudRunJobService cloudRunJobService;
    private final ObjectMapper objectMapper;

    @PostMapping("/exec/{jobName}")
    public ResponseEntity<?> execute(@PathVariable String jobName, @RequestBody Object payload) throws Exception {
        JobContext context = new JobContext();
        context.setTaskId(UUID.randomUUID().toString());
        context.setPayload(payload);
        context.setTaskCount(50);
        log.info("Executing job {}", objectMapper.writeValueAsString(context));
        this.cloudRunJobService.runCloudRunJob(jobName, context);
        return ResponseEntity.accepted().build();
    }
}
