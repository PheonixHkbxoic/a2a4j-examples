package io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.router;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.input.PromptTemplate;
import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClient;
import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClientSet;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.error.JsonRpcError;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import io.github.pheonixhkbxoic.a2a4j.core.util.Uuid;
import io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.schema.ExecuteContext;
import io.github.pheonixhkbxoic.a2a4j.host.autoconfiguration.A2a4jAgentsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/26 15:45
 * @desc agent router
 */
@Slf4j
public class AgentRouter {
    private final A2AClientSet clientSet;
    private final A2a4jAgentsProperties a2a4jAgentsProperties;

    private final ChatLanguageModel model;
    private final StreamingChatLanguageModel streamingModel;

    public AgentRouter(A2AClientSet a2aClientSet, A2a4jAgentsProperties a2a4jAgentsProperties,
                       ChatLanguageModel model, StreamingChatLanguageModel streamingModel) {
        this.clientSet = a2aClientSet;
        this.a2a4jAgentsProperties = a2a4jAgentsProperties;
        this.model = model;
        this.streamingModel = streamingModel;
    }


    public Mono<String> chat(String userId, String sessionId, String query) {
        SystemMessage systemMessage = this.buildSystemMessage(userId, sessionId);
        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, UserMessage.from(query))
                .build();
        StopWatch stopWatch = new StopWatch();
        return Mono.fromSupplier(() -> {
                    stopWatch.start("router");
                    String text = model.chat(request).aiMessage().text();
                    log.info("chat response text: {}", text);
                    text = text.replaceFirst("^```json|```$", "");
                    ExecuteContext ec = Util.fromJson(text, ExecuteContext.class);
                    stopWatch.stop();
                    return ec;
                })
                .flatMap(ec -> {
                    // return the answer directly
                    String activeAgent = ec.getResult().getActiveAgent();
                    A2AClient agentClient = clientSet.getByName(activeAgent);
                    if (ExecuteContext.DEFAULT_AGENT_NAME.equals(activeAgent) || agentClient == null) {
                        log.debug("chat statistics: {}", stopWatch.prettyPrint());
                        return Mono.just(ec.getResult().getAnswer());
                    }

                    // route suitable agent
                    stopWatch.start("router agent");
                    log.debug("route to agent: {}", activeAgent);
                    Mono<String> result = routeToAgent(agentClient, sessionId, query);
                    stopWatch.stop();

                    log.debug("chat statistics: {}", stopWatch.prettyPrint());
                    return result;
                });
    }

    public Flux<String> chatStream(String userId, String sessionId, String query) {
        SystemMessage systemMessage = this.buildSystemMessage(userId, sessionId);
        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, UserMessage.from(query))
                .build();
        return Flux.create(sink -> {
            StringBuilder cache = new StringBuilder();
            streamingModel.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String s) {
                    cache.append(s);
                }

                @Override
                public void onCompleteResponse(ChatResponse chatResponse) {
                    String text = cache.toString();
                    log.debug("chatStream router response: {}", text);
                    text = text.replaceFirst("^```json|```$", "");
                    ExecuteContext ec = Util.fromJson(text, ExecuteContext.class);
                    // return the answer directly
                    String activeAgent = ec.getResult().getActiveAgent();
                    A2AClient agentClient = clientSet.getByName(activeAgent);
                    if (ExecuteContext.DEFAULT_AGENT_NAME.equals(activeAgent) || agentClient == null) {
                        String answer = ec.getResult().getAnswer();
                        sink.next(answer);
                        sink.complete();
                    } else {
                        // route suitable agent
                        log.debug("chatStream route to agent: {}", activeAgent);
                        routerToAgentStream(agentClient, sessionId, query, sink);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    sink.error(throwable);
                }
            });
        });
    }

    private Mono<String> routeToAgent(A2AClient agentClient, String sessionId, String query) {
        TaskSendParams taskSendParams = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .message(Message.builder().parts(List.of(new TextPart(query))).role(Role.USER).build())
                .pushNotification(a2a4jAgentsProperties.getNotification())
                .build();
        return agentClient.sendTask(taskSendParams)
                .map(sendTaskResponse -> {
                    JsonRpcError error = sendTaskResponse.getError();
                    if (error != null) {
                        throw new RuntimeException(error.getMessage());
                    }
                    return sendTaskResponse.getResult().getArtifacts().stream()
                            .flatMap(a -> a.getParts().stream())
                            .filter(p -> p.getType().equals(new TextPart().getType()))
                            .map(p -> ((TextPart) p).getText())
                            .collect(Collectors.joining());
                });

    }

    private void routerToAgentStream(A2AClient agentClient, String sessionId, String query, FluxSink<String> sink) {
        TaskSendParams taskSendParams = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .message(Message.builder().parts(List.of(new TextPart(query))).role(Role.USER).build())
                .pushNotification(a2a4jAgentsProperties.getNotification())
                .build();
        agentClient.sendTaskSubscribe(taskSendParams)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(r -> {
                    UpdateEvent event = r.getResult();
                    if (event instanceof TaskStatusUpdateEvent) {
                        TaskStatus status = ((TaskStatusUpdateEvent) event).getStatus();
                        if (status.getMessage() == null) {
                            return Flux.empty();
                        }
                        return Flux.fromStream(status.getMessage().getParts().stream());
                    }
                    Artifact artifact = ((TaskArtifactUpdateEvent) r.getResult()).getArtifact();
                    return Flux.fromStream(artifact.getParts().stream());
                })
                .filter(p -> new TextPart().getType().equals(p.getType()))
                .map(p -> ((TextPart) p).getText())
//                .doOnNext(s -> log.info("client received: {}", s))
                .filter(s -> s != null && !s.isBlank())
                .doOnComplete(sink::complete)
                .doOnError(sink::error)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(sink::next);
    }

    protected SystemMessage buildSystemMessage(String userId, String sessionId) {
        Map<String, Object> dataVars = new HashMap<>();
        dataVars.put("envVariables", Util.toJson(Map.of("userId", userId, "sessionId", sessionId)));
        dataVars.put("listRemoteAgents", listRemoteAgents());
        dataVars.put("conversationHistories", "");
        dataVars.put("outputJsonSchema", buildOutputJsonSchema());

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

    /**
     * doesn't work
     */
    protected ResponseFormat buildResponseFormat() {
        JsonObjectSchema resultSchema = JsonObjectSchema.builder()
                .addStringProperty("activeAgent", "the active agent name")
                .addStringProperty("answer", "the answer of user's question if not found suitable agent")
                .required("activeAgent")
                .build();
        JsonSchema root = JsonSchema.builder()
                .name("ExecuteContext")
                .rootElement(JsonObjectSchema.builder()
                        .addStringProperty("userId", "user id")
                        .addStringProperty("sessionId", "session id")
                        .addStringProperty("state", "available state: over means find a suitable agent; input_required means extra message or data is required")
                        .addProperty("result", resultSchema)
                        .required("userId", "sessionId", "state", "result")
                        .build())
                .build();
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(root)
                .build();
    }

    protected String buildOutputJsonSchema() {
        return """
                {
                  "userId": "user id",
                  "sessionId": "session id",
                  "state": "available state: over means find a suitable agent; input_required means extra message or data is required",
                  "result": {
                    "activeAgent": "the active agent name",
                    "answer": "the answer of user's question if not found suitable agent"
                  }
                }
                """;
    }
}
