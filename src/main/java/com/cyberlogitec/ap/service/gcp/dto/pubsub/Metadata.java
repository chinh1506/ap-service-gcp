package com.cyberlogitec.ap.service.gcp.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {
    public String name;
    public String namespace;
    public String selfLink;
    public String uid;
    public String resourceVersion;
    public Integer generation;
    public String creationTimestamp;
    public Map<String, String> labels;
    public Map<String, String> annotations;
    public List<OwnerReference> ownerReferences;
}
