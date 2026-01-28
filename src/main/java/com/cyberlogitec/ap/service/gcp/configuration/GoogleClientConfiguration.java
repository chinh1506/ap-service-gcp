package com.cyberlogitec.ap.service.gcp.configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.script.ScriptScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Configuration
public class GoogleClientConfiguration {

    @Value("${spring.cloud.gcp.cloud-run-app-name}")
    private String applicationName;

    @Value("${GOOGLE_CREDENTIALS_PATHS}")
    private List<String> CREDENTIALS_FILE_PATHS;

    final int CONNECT_TIMEOUT = 3 * 60000;
    final int READ_TIMEOUT = 5 * 60000;

    private static final List<String> GLOBAL_SCOPES = Arrays.asList(
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE,
            ScriptScopes.SCRIPT_PROJECTS,
            "https://www.googleapis.com/auth/script.scriptapp"
    );

    @Bean
    @Profile({"job-dev", "service-dev"})
    public GoogleClientPool googleClientPool() throws GeneralSecurityException, IOException {
        final NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        List<Sheets> sheetsList = new ArrayList<>();
        List<Drive> driveList = new ArrayList<>();

        for (String path : CREDENTIALS_FILE_PATHS) {
            GoogleCredentials credential = GoogleCredentials
                    .fromStream(Objects.requireNonNull(GoogleClientConfiguration.class.getResourceAsStream(path)))
                    .createScoped(GLOBAL_SCOPES);

            HttpRequestInitializer requestInitializer = request -> {
                new HttpCredentialsAdapter(credential).initialize(request);
                request.setConnectTimeout(CONNECT_TIMEOUT);
                request.setReadTimeout(READ_TIMEOUT); // Khắc phục lỗi Read timed out
            };

            Sheets sheets = new Sheets.Builder(transport, jsonFactory, requestInitializer)
                    .setApplicationName(applicationName)
                    .build();
            sheetsList.add(sheets);

            Drive drive = new Drive.Builder(transport, jsonFactory, requestInitializer)
                    .setApplicationName(applicationName)
                    .build();
            driveList.add(drive);

        }

        return new GoogleClientPool(sheetsList, driveList);
    }


    @Bean
    @Profile({"job-prod", "service-prod"})
    public GoogleClientPool googleClientPoolProd() throws GeneralSecurityException, IOException {
        final NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        List<Sheets> sheetsList = new ArrayList<>();
        List<Drive> driveList = new ArrayList<>();


        for (String path : CREDENTIALS_FILE_PATHS) {
            File cloudFile = new File(path);
            try (FileInputStream credentialsStream = new FileInputStream(cloudFile)) {
                GoogleCredentials credential = GoogleCredentials
                        .fromStream(Objects.requireNonNull(credentialsStream))
                        .createScoped(GLOBAL_SCOPES);

                HttpRequestInitializer requestInitializer = request -> {
                    new HttpCredentialsAdapter(credential).initialize(request);
                    request.setConnectTimeout(CONNECT_TIMEOUT);
                    request.setReadTimeout(READ_TIMEOUT); // Khắc phục lỗi Read timed out
                };

                Sheets sheets = new Sheets.Builder(transport, jsonFactory, requestInitializer)
                        .setApplicationName(applicationName)
                        .build();
                sheetsList.add(sheets);

                Drive drive = new Drive.Builder(transport, jsonFactory, requestInitializer)
                        .setApplicationName(applicationName)
                        .build();
                driveList.add(drive);

            }
        }
        return new GoogleClientPool(sheetsList, driveList);
    }


}
