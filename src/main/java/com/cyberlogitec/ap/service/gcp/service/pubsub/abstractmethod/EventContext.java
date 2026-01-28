package com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod;

import com.cyberlogitec.ap.service.gcp.dto.pubsub.PubSubMessage;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventContext {
//    private String eventId;
    private WorkflowState workflowState;
    private Object payload;
    private PubSubMessage pubSubMessage;
}
