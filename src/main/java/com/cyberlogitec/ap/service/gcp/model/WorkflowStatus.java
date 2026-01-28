package com.cyberlogitec.ap.service.gcp.model;

public enum WorkflowStatus {
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    private final String status;

    private WorkflowStatus(String status) {
        this.status = status;
    }

}
