package com.cyberlogitec.ap_service_gcp.controller;

import com.cyberlogitec.ap_service_gcp.dto.request.NotifyToPicRequest;
import com.cyberlogitec.ap_service_gcp.service.BookingJobService;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/aedomi/bkg")
@AllArgsConstructor
@Profile({"service-dev","service-prod"})
public class AeDomiBookingController {
    private final BookingJobService bookingJobService;

    @PostMapping("/create-child-folder-external")
    public ResponseEntity<?> createChildFoldersExternal(@RequestBody Object payload) throws Exception {
        Utilities.logMemory("Begin CreateChildFoldersExternal");
        this.bookingJobService.prepareToCreateChildFoldersExternal(payload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/notify-to-pic-external")
    public ResponseEntity<String> testEndpoint(@RequestBody NotifyToPicRequest notifyToPicRequest) throws IOException {

        this.bookingJobService.notifyToPIC(notifyToPicRequest);

        return ResponseEntity.ok("Test endpoint is working!");
    }

}
