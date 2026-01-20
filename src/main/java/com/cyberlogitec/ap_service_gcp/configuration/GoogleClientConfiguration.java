package com.cyberlogitec.ap_service_gcp.configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Configuration
public class GoogleClientConfiguration {

    @Value("${spring.cloud.gcp.cloud-run-app-name}")
    private String APPLLICATION_NAME;

    private static final String[] CREDENTIALS_FILE_PATHS = {
            "/secrets/credentials-0.json",
            "/secrets/credentials-1.json",
            "/secrets/credentials-2.json",
            "/secrets/credentials-3.json",
            "/secrets/credentials-4.json"
    };
    private static final List<String> GLOBAL_SCOPES = Arrays.asList(
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE
    );

    @Bean
    public GoogleClientPool googleClientPool() throws GeneralSecurityException, IOException {

        final NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        List<Sheets> sheetsList = new ArrayList<>();
        List<Drive> driveList = new ArrayList<>();

        for (String path : CREDENTIALS_FILE_PATHS) {
            GoogleCredentials credential = GoogleCredentials
                    .fromStream(Objects.requireNonNull(GoogleClientConfiguration.class.getResourceAsStream(path)))
                    .createScoped(GLOBAL_SCOPES);

            Sheets sheets = new Sheets.Builder(transport, jsonFactory, new HttpCredentialsAdapter(credential))
                    .setApplicationName(APPLLICATION_NAME)
                    .build();
            sheetsList.add(sheets);

            Drive drive = new Drive.Builder(transport, jsonFactory, new HttpCredentialsAdapter(credential))
                    .setApplicationName(APPLLICATION_NAME)
                    .build();
            driveList.add(drive);

        }
        return new GoogleClientPool(sheetsList, driveList);
    }


}
