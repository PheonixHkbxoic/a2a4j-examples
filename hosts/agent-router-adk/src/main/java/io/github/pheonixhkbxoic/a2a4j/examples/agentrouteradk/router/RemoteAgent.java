package io.github.pheonixhkbxoic.a2a4j.examples.agentrouteradk.router;


import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClient;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.error.JsonRpcError;
import io.github.pheonixhkbxoic.a2a4j.host.autoconfiguration.A2a4jAgentsProperties;
import io.github.pheonixhkbxoic.adk.runtime.AdkAgentInvoker;
import io.github.pheonixhkbxoic.adk.runtime.ExecutableContext;
import io.github.pheonixhkbxoic.adk.runtime.ResponseFrame;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/5/8 23:48
 * @desc interact with remote A2AServer by A2AClient
 */
public class RemoteAgent implements AdkAgentInvoker {
    private final A2AClient agentClient;
    private final A2a4jAgentsProperties a2a4jAgentsProperties;

    public RemoteAgent(A2AClient agentClient, A2a4jAgentsProperties a2a4jAgentsProperties) {
        this.agentClient = agentClient;
        this.a2a4jAgentsProperties = a2a4jAgentsProperties;
    }

    @Override
    public Mono<ResponseFrame> invoke(ExecutableContext context) {
        String sessionId = context.getPayload().getSessionId();
        String taskId = context.getPayload().getTaskId();
        String query = context.getPayload().getMessage();

        TaskSendParams taskSendParams = TaskSendParams.builder()
                .id(taskId)
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
                    String answer = sendTaskResponse.getResult().getArtifacts().stream()
                            .flatMap(a -> a.getParts().stream())
                            .filter(p -> p.getType().equals(new TextPart().getType()))
                            .map(p -> ((TextPart) p).getText())
                            .collect(Collectors.joining());
                    ResponseFrame responseFrame = new ResponseFrame();
                    responseFrame.setMessage(answer);
                    return responseFrame;
                });
    }

    @Override
    public Flux<ResponseFrame> invokeStream(ExecutableContext context) {
        String sessionId = context.getPayload().getSessionId();
        String taskId = context.getPayload().getTaskId();
        String query = context.getPayload().getMessage();

        TaskSendParams taskSendParams = TaskSendParams.builder()
                .id(taskId)
                .sessionId(sessionId)
                .message(Message.builder().parts(List.of(new TextPart(query))).role(Role.USER).build())
                .pushNotification(a2a4jAgentsProperties.getNotification())
                .build();
        return Flux.create(sink -> agentClient.sendTaskSubscribe(taskSendParams)
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
                .subscribe(str -> {
                    ResponseFrame responseFrame = new ResponseFrame();
                    responseFrame.setMessage(str);
                    sink.next(responseFrame);
                }));

    }
}
