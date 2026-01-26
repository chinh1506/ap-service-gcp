package com.cyberlogitec.ap_service_gcp.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/multi-trade/ap/master")
public class MultiTradeApMasterController {


    @PostMapping("/create-input-file")
    public ResponseEntity<?> createInputFile() {


        return ResponseEntity.ok("Hello World");
    }



}
