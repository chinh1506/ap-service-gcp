package com.cyberlogitec.ap_service_gcp.configuration;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Configuration
public class GoogleClientConfiguration {
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private final String APPLLICATION_NAME = "cloud-run-app";


    @Bean
    public HttpTransport httpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public HttpRequestInitializer googleCredentials() throws IOException {

        GoogleCredentials credentials =
                ServiceAccountCredentials
                        .fromStream(Objects.requireNonNull(GoogleClientConfiguration.class.getResourceAsStream(CREDENTIALS_FILE_PATH)))
                        .createScoped(List.of(
                                SheetsScopes.SPREADSHEETS,
                                DriveScopes.DRIVE
                        ));
//        HttpRequestInitializer initializer= new HttpCredentialsAdapter(credentials);
//        initializer.
        return new HttpCredentialsAdapter(credentials);
    }


    @Bean
    public Sheets sheets(HttpRequestInitializer initializer) throws Exception {

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                initializer
        ).setApplicationName(APPLLICATION_NAME).build();
    }

    @Bean
    public Drive drive(HttpRequestInitializer initializer) throws Exception {

        HttpRequestInitializer requestInitializer = httpRequest -> {
            initializer.initialize(httpRequest);
            httpRequest.setConnectTimeout(60000);  // 3 minutes connect timeout
            httpRequest.setReadTimeout(2 * 60000);  // 3 minutes read timeout
        };

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer
        ).setApplicationName(APPLLICATION_NAME).build();
    }
}
