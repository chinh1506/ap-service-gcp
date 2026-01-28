package com.cyberlogitec.ap.service.gcp.dto;

import lombok.Data;

import java.util.Set;

@Data
public class EmailDTO {
    private String fo;
    private Set<String> to;
    private Set<String> cc;
    private Set<String> bcc;
    private String subject;
    private String body;
}
