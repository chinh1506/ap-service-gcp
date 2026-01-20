package com.cyberlogitec.ap_service_gcp.configuration;

import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GoogleClientPool {
    private final List<Sheets> sheetsClients;
    private final List<Drive> driveClients;

    private final AtomicInteger index = new AtomicInteger(0);

    public GoogleClientPool(List<Sheets> sheetsClients, List<Drive> driveClients) {
        this.sheetsClients = sheetsClients;
        this.driveClients = driveClients;
    }

    public Sheets getNextSheetsClient() {
        return sheetsClients.get(getRoundRobinIndex());
    }

    public Drive getNextDriveClient() {
        return driveClients.get(getRoundRobinIndex());
    }

    private int getRoundRobinIndex() {
        int i = index.getAndIncrement() % sheetsClients.size();
        if (i < 0) {
            index.set(0);
            i = 0;
        }
        return i;
    }

    public int getPoolSize() {
        return sheetsClients.size();
    }
}
