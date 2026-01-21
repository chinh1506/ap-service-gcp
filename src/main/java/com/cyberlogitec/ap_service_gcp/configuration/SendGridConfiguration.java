package com.cyberlogitec.ap_service_gcp.configuration;

import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SendGridConfiguration {
    @Value("${SENDGRID_API_KEY}")
    private String SENDGRID_API_KEY;
    @Value("${SENDER_EMAIL}")
    private String SENDER_EMAIL;
    @Value("${SENDER_NAME}")
    private String SENDER_NAME ;

    @Bean
    public SendGrid sendGrid() {
        return new SendGrid(SENDGRID_API_KEY);
    }

    @Bean
    public Email fromEmail() {
        return new Email(SENDER_EMAIL, SENDER_NAME);
    }
}
