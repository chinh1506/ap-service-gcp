package com.cyberlogitec.ap_service_gcp.job.extension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobContext {
    private String jobId;
    private Object payload;
    private int taskCount = 1;
}
