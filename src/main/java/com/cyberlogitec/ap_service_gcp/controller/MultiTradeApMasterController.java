package com.cyberlogitec.ap_service_gcp.controller;


import com.cyberlogitec.ap_service_gcp.dto.multitrade.CreateInputFileRequest;
import com.cyberlogitec.ap_service_gcp.service.MultiTradeApMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/multi-trade/ap/master")
@RequiredArgsConstructor
public class MultiTradeApMasterController {

    private final MultiTradeApMasterService multiTradeApMasterService;


    @PostMapping("/create-input-file")
    public ResponseEntity<?> createInputFile(@RequestBody CreateInputFileRequest createInputFileRequest) throws IOException {

        this.multiTradeApMasterService.prepareToCreateFoFiles(createInputFileRequest);

        return ResponseEntity.ok("Hello World");
    }



}
