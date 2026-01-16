package com.cyberlogitec.ap_service_gcp.controller;

import com.cyberlogitec.ap_service_gcp.service.CloudRunJobService;
import com.cyberlogitec.ap_service_gcp.service.DriveServiceHelper;
import com.google.api.services.sheets.v4.Sheets;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController

public class TestController {
    private final Sheets sheetsService;
    private final DriveServiceHelper driveServiceHelper;
    private final CloudRunJobService cloudRunJobService;
    private final Firestore firestore;

    public TestController(Sheets sheets, DriveServiceHelper driveServiceHelper, CloudRunJobService cloudRunJobService, Firestore firestore) {
        this.sheetsService = sheets;
        this.driveServiceHelper = driveServiceHelper;
        this.cloudRunJobService = cloudRunJobService;
        this.firestore = firestore;
    }


    @GetMapping("/test")
    public Object getSheetName() throws IOException {
//    driveServiceHelper
//        FolderStructure folderStructure = driveServiceHelper.getExistingFolderStructure("1sWJD5TwY9ufmKmGG6Tf_gbcimESWiCQH");
//        System.out.println(folderStructure);
        DocumentReference test = firestore.collection("job_collection").document("test");

        test.set(Map.of("key", "value"));

        this.cloudRunJobService.getJobExecutionDetails("ap-job-2g8jp");

        return null;
    }
}
