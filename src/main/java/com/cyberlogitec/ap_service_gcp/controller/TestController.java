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
import java.util.concurrent.ExecutionException;

@RestController
public class TestController {
    private final CloudRunJobService cloudRunJobService;

    public TestController(CloudRunJobService cloudRunJobService) {
        this.cloudRunJobService = cloudRunJobService;
    }


    @GetMapping("/test")
    public Object getSheetName() throws  ExecutionException, InterruptedException {
//        cloudRunJobService.setJobCache("execution-name-123", "job-id-456");
//        driveServiceHelper
//        FolderStructure folderStructure = driveServiceHelper.getExistingFolderStructure("1sWJD5TwY9ufmKmGG6Tf_gbcimESWiCQH");
//        System.out.println(folderStructure);
//        DocumentReference test = firestore.collection("job_collection").document("test");

//        test.set(Map.of("key", "value"));
//
//        this.cloudRunJobService.getJobExecutionDetails("ap-job-2g8jp");

        return null;
    }
    @GetMapping("/test-get")
    public Object getData() throws  ExecutionException, InterruptedException {
       return  cloudRunJobService.getJobValue("execution-name-123");
//    driveServiceHelper
//        FolderStructure folderStructure = driveServiceHelper.getExistingFolderStructure("1sWJD5TwY9ufmKmGG6Tf_gbcimESWiCQH");
//        System.out.println(folderStructure);
//        DocumentReference test = firestore.collection("job_collection").document("test");

//        test.set(Map.of("key", "value"));
//
//        this.cloudRunJobService.getJobExecutionDetails("ap-job-2g8jp");

    }
}
