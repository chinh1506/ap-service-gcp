package com.cyberlogitec.ap_service_gcp.model;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobCache{
    @DocumentId
    private String executionName;
    private String jobId;
}
