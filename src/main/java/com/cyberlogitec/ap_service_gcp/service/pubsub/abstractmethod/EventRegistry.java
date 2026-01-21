package com.cyberlogitec.ap_service_gcp.service.pubsub.abstractmethod;

import com.cyberlogitec.ap_service_gcp.job.extension.JobPlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EventRegistry {
    private final Map<String, EventPlugin> eventMap;

    public EventRegistry(List<EventPlugin> plugins) {
        this.eventMap = plugins.stream()
                .collect(Collectors.toMap(
                        EventPlugin::getEventName,
                        Function.identity()
                ));
    }

    public EventPlugin getPlugin(String eventName) {
        EventPlugin plugin = eventMap.get(eventName);
        if (plugin == null) {
            throw new IllegalArgumentException("Unknown event: " + eventName);
        }
        return plugin;
    }
}
