package com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventRunner {
    private final EventRegistry eventRegistry;

    public void run(EventContext context) throws Exception {
        eventRegistry.getPlugin(context.getWorkflowState().getCurrentStepName()).handle(context);
    }
}
