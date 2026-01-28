package com.cyberlogitec.ap.service.gcp.service;

import com.cyberlogitec.ap.service.gcp.dto.pubsub.PubSubMessage;
import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.cyberlogitec.ap.service.gcp.repository.WorkflowStateRepository;
import com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod.EventContext;
import com.cyberlogitec.ap.service.gcp.service.pubsub.abstractmethod.EventRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PubSubService {
    private final ObjectMapper objectMapper;
    private final CloudRunJobService cloudRunJobService;
    private final WorkflowStateRepository workflowStateRepository;
    private final EventRunner eventRunner;

    public void handleCallBack(Map<String, Object> pubSubMessage) throws Exception {
        Map<String, Object> message = (Map<String, Object>) pubSubMessage.get("message");
        String dataBase64 = (String) message.get("data");
        String logJson = new String(Base64.getDecoder().decode(dataBase64));
        JsonNode rootNode = objectMapper.readTree(logJson);
        PubSubMessage subMessage = objectMapper.treeToValue(rootNode, PubSubMessage.class);

        Map<String, String> labelsNode = subMessage.getLabels();
        String executionName = labelsNode.get("run.googleapis.com/execution_name");

        WorkflowState workflowState = this.workflowStateRepository.findByExecutionName(executionName);

        EventContext eventContext = EventContext.builder()
                .workflowState(workflowState)
                .pubSubMessage(subMessage)
                .build();
        this.eventRunner.run(eventContext);
    }


}
