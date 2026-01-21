package com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod;

public interface EventPlugin {
    /**
     * Unique event name need to the same with jobName
     */
    String getEventName();

    /**
     * handle job
     */
    void execute(EventContext context) throws Exception;
}
