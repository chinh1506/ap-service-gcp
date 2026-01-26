package com.cyberlogitec.ap_service_gcp.controller;

import com.cyberlogitec.ap_service_gcp.service.CloudRunJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
public class TestController {
    private final CloudRunJobService cloudRunJobService;
//    private final AppsScriptServiceHelper appsScriptServiceHelper;

    @GetMapping("/test")
    public Object getSheetName() throws GeneralSecurityException, IOException {

//        this.appsScriptServiceHelper.callAppsScriptFunction("myFunction", List.of("chinh","18"));
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
