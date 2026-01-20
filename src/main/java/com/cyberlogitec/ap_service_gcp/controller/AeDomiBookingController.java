package com.cyberlogitec.ap_service_gcp.controller;

import com.cyberlogitec.ap_service_gcp.service.BookingJobService;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/aedomi/bkg")
@AllArgsConstructor
public class AeDomiBookingController {
    private final BookingJobService bookingJobService;

    @PostMapping("/CreateChildFoldersExternal")
    public ResponseEntity<?> createChildFoldersExternal(@RequestBody Object payload) throws Exception {
        Utilities.logMemory("Begin CreateChildFoldersExternal");
        this.bookingJobService.prepareToCreateChildFoldersExternal(payload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/job-result")
    public ResponseEntity<String> handleJobResult(@RequestBody Map<String, Object> pubSubMessage) {
        System.out.println("====== JOB COMPLETED ======");
        if (!pubSubMessage.containsKey("message")) {
            return ResponseEntity.badRequest().body("Invalid Pub/Sub format");
        }
        try {
            this.bookingJobService.handleBookingCallBackResult(pubSubMessage);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/exec/NotifyToPICExternal")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Test endpoint is working!");
    }


}
