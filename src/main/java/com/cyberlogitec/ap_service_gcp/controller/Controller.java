package com.cyberlogitec.ap_service_gcp.controller;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController

public class Controller {
    @Value("${NAME:World}")
    String name;
    private final Sheets sheetsService;

    public Controller(Sheets sheets) {
        this.sheetsService = sheets;
    }


    @GetMapping("/ssa")
    public String getSheetName(@RequestParam String ssaId) throws IOException {
//        this.sheetsService.spreadsheets().
        ValueRange response = this.sheetsService.spreadsheets().values()
                .get(ssaId, "BSA_Input!A1:C10")
                .execute();

        System.out.println(response);
        return "Hello, " + name + "!";
    }
}
