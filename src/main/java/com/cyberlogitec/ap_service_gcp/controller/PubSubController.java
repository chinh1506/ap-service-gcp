package com.cyberlogitec.ap_service_gcp.controller;

import com.cyberlogitec.ap_service_gcp.service.BookingJobService;
import com.cyberlogitec.ap_service_gcp.service.PubSubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class PubSubController {
    private final BookingJobService bookingJobService;
    private final PubSubService pubSubService;

    @PostMapping("/job-result")
    public ResponseEntity<String> handleJobResult(@RequestBody Map<String, Object> pubSubMessage) throws Exception {

        System.out.println("====== JOB COMPLETED ======");
        if (!pubSubMessage.containsKey("message")) {
            return ResponseEntity.badRequest().body("Invalid Pub/Sub format");
        }
        try {
            this.pubSubService.handleCallBack(pubSubMessage);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.accepted().build();
    }
}
