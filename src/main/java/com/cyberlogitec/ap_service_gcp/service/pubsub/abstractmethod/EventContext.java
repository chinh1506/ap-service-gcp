package com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod;

import com.cyberlogitec.ap_service_gcp.dto.pubsub.PubSubMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventContext {
    private String eventId;
    private Object payload;
    private PubSubMessage pubSubMessage;
}
