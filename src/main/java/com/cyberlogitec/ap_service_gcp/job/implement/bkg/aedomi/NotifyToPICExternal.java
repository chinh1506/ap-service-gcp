package com.cyberlogitec.ap_service_gcp.job.implement.bkg.aedomi;

import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import org.springframework.stereotype.Component;

@Component// bean này sẽ được quét và đăng ký tự động bởi Spring
public class NotifyToPICExternal implements JobPlugin {
    @Override
    public String getJobName() {
        return "NotifyToPICExternal";
    }

    @Override
    public void execute(JobContext context) throws Exception {
        System.out.println("NotifyToPICExternal bắt đầu xử lý logic");
    }
}
