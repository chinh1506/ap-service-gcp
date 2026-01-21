package com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventRunner {
    private final EventRegistry eventRegistry;

    public void run(String eventName, EventContext context) throws Exception {
        eventRegistry.getPlugin(eventName).execute(context);
    }
}
