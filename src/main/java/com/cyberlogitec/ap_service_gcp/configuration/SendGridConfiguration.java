package com.cyberlogitec.ap_service_gcp.configuration;

import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SendGridConfiguration {
    @Bean
    public SendGrid sendGrid() {
        String apiKey="";
        return new SendGrid(apiKey);
    }
    @Bean
    public Email fromEmail() {
        String fromEmail = "";
        String fromName = "";
        return new Email(fromEmail, fromName);
    }
}
