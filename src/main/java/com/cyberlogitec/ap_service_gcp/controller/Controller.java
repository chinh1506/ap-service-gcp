package com.cyberlogitec.ap_service_gcp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    @Value("${NAME:World}")
    String name;

    @GetMapping("/hello")
    public String hello() {
        return "Hello, "+name+"!";
    }
}
