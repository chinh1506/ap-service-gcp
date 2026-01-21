package com.cyberlogitec.ap_service_gcp.dto.pubsub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Container {
    public String image;
    public List<EnvVar> env;
    public Resources resources;
}
