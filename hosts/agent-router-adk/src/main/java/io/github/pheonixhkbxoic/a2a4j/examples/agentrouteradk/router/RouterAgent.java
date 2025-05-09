package io.github.pheonixhkbxoic.a2a4j.examples.agentrouteradk.router;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.input.PromptTemplate;
import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClientSet;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import io.github.pheonixhkbxoic.adk.Payload;
import io.github.pheonixhkbxoic.adk.runtime.AdkAgentInvoker;
import io.github.pheonixhkbxoic.adk.runtime.ExecutableContext;
import io.github.pheonixhkbxoic.adk.runtime.ResponseFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author PheonixHkbxoic
 * @date 2025/5/9 00:03
 * @desc request LLM eg.DeepSeek, response suitable agent name with activeAgent
 */
@Component
@Slf4j
public class RouterAgent implements AdkAgentInvoker {
    public static final String ACTIVE_AGENT = "activeAgent";
    private final A2AClientSet clientSet;
    private final ChatLanguageModel model;

    public RouterAgent(A2AClientSet clientSet, ChatLanguageModel model) {
        this.clientSet = clientSet;
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<ResponseFrame> invoke(ExecutableContext context) {
        Payload payload = context.getPayload();
        String userId = payload.getUserId();
        String sessionId = payload.getSessionId();
        String query = payload.getMessage();
        SystemMessage systemMessage = this.buildSystemMessage(userId, sessionId);
        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, UserMessage.from(query))
                .build();

        try {
            String text = model.chat(request).aiMessage().text();
            log.info("router agent response text: {}", text);
            text = text.replaceFirst("^```json|```$", "");
            Map<String, Object> response = Util.fromJson(text, HashMap.class);
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            context.setMetadata(result);
        } catch (Exception e) {
            context.setMetadata(new HashMap<>());
            log.error("router agent error: {}", e.getMessage(), e);
        }
        return Mono.empty();
    }

    @Override
    public Flux<ResponseFrame> invokeStream(ExecutableContext context) {
        return this.invoke(context).flux();
    }


    protected SystemMessage buildSystemMessage(String userId, String sessionId) {
        Map<String, Object> dataVars = new HashMap<>();
        dataVars.put("envVariables", Util.toJson(Map.of("userId", userId, "sessionId", sessionId)));
        dataVars.put("listRemoteAgents", listRemoteAgents());
        dataVars.put("conversationHistories", "");
        dataVars.put("outputJsonSchema", this.buildOutputJsonSchema());

        SystemMessage systemMessage = PromptTemplate.from(
                        """
                                You are an expert in agent delegation, whose job is to determine which agent should handle a user's request. \
                                If none of them are suitable, then the activeAgent must be set `None`, \
                                Answer the user's questions and put the answers in the `result.answer` field
                                
                                <envVariables>：
                                {{envVariables}}
                                
                                <agentRemoteAgents>：
                                {{listRemoteAgents}}
                                
                                <conversationHistories>:
                                {{conversationHistories}}
                                
                                <outputJsonSchema>:
                                {{outputJsonSchema}}
                                
                                """)
                .apply(dataVars)
                .toSystemMessage();
        log.debug("systemMessage: {}", systemMessage.text());
        return systemMessage;
    }

    protected String listRemoteAgents() {
        if (clientSet.isEmpty()) {
            return "";
        }
        List<Map<String, String>> agents = clientSet.toNameMap().values().stream()
                .map(client -> Map.of("name", client.getAgentCard().getName(), "description", client.getAgentCard().getDescription()))
                .toList();
        return Util.toJson(agents);
    }

    protected String buildOutputJsonSchema() {
        return """
                {
                  "userId": "user id",
                  "sessionId": "session id",
                  "state": "available state: over means find a suitable agent; input_required means extra message or data is required",
                  "result": {
                    "%s": "the active agent name",
                    "answer": "the answer of user's question if not found suitable agent"
                  }
                }
                """.formatted(ACTIVE_AGENT);
    }
}
