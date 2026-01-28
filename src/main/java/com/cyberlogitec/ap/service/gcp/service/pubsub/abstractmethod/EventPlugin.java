package com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod;

public interface EventPlugin {
    /**
     * Job name to listen
     */
    String listeningOnJob();

    /**
     * handle job
     */
    void handle(EventContext context) throws Exception;
}
