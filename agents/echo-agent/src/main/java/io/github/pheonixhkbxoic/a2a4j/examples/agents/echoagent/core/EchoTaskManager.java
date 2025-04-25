package io.github.pheonixhkbxoic.a2a4j.examples.agents.echoagent.core;


import io.github.pheonixhkbxoic.a2a4j.core.core.InMemoryTaskManager;
import io.github.pheonixhkbxoic.a2a4j.core.core.PushNotificationSenderAuth;
import io.github.pheonixhkbxoic.a2a4j.core.spec.ValueError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.error.InternalError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.error.InvalidParamsError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.*;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/14 15:33
 * @desc
 */
@Slf4j
@Component
public class EchoTaskManager extends InMemoryTaskManager {
    // wire agent
    private final io.github.pheonixhkbxoic.a2a4j.examples.agents.echoagent.core.EchoAgent agent;
    // agent support modes
    private final List<String> supportModes = Arrays.asList("text", "file", "data");

    public EchoTaskManager(@Autowired io.github.pheonixhkbxoic.a2a4j.examples.agents.echoagent.core.EchoAgent agent, @Autowired PushNotificationSenderAuth pushNotificationSenderAuth) {
        this.agent = agent;
        // must autowired, keep PushNotificationSenderAuth instance unique global
        this.pushNotificationSenderAuth = pushNotificationSenderAuth;
    }

    @Override
    public Mono<SendTaskResponse> onSendTask(SendTaskRequest request) {
        log.info("onSendTask request: {}", request);
        TaskSendParams ps = request.getParams();
        // 1. check
        JsonRpcResponse<Object> error = this.validRequest(request);
        if (error != null) {
            return Mono.just(new SendTaskResponse(request.getId(), error.getError()));
        }
        // check and set pushNotification
        if (ps.getPushNotification() != null) {
            boolean verified = this.verifyPushNotificationInfo(ps.getId(), ps.getPushNotification());
            if (!verified) {
                return Mono.just(new SendTaskResponse(request.getId(), new InvalidParamsError("Push notification URL is invalid")));
            }
        }

        // save
        this.upsertTask(ps);
        Task taskWorking = this.updateStore(ps.getId(), new TaskStatus(TaskState.WORKING), null);
        this.sendTaskNotification(taskWorking);

        // 2. agent invoke
        return this.agentInvoke(ps).map(artifacts -> {
            // 4. save and notification
            Task taskCompleted = this.updateStore(ps.getId(), new TaskStatus(TaskState.COMPLETED), artifacts);
            this.sendTaskNotification(taskCompleted);

            Task taskSnapshot = this.appendTaskHistory(taskCompleted, 3);
            return new SendTaskResponse(taskSnapshot);
        });
    }

    @Override
    public Mono<? extends JsonRpcResponse<?>> onSendTaskSubscribe(SendTaskStreamingRequest request) {
        return Mono.fromCallable(() -> {
            log.info("onSendTaskSubscribe request: {}", request);
            TaskSendParams ps = request.getParams();
            String taskId = ps.getId();

            try {
                // 1. check
                JsonRpcResponse<Object> error = this.validRequest(request);
                if (error != null) {
                    throw new ValueError(error.getError().getMessage());
                }
                // check and set pushNotification
                if (ps.getPushNotification() != null) {
                    boolean verified = this.verifyPushNotificationInfo(taskId, ps.getPushNotification());
                    if (!verified) {
                        return new SendTaskResponse(request.getId(), new InvalidParamsError("Push notification URL is invalid"));
                    }
                }

                // save
                this.upsertTask(ps);
                Task taskWorking = this.updateStore(taskId, new TaskStatus(TaskState.WORKING), null);
                this.sendTaskNotification(taskWorking);

                this.initEventQueue(taskId, false);

                // 3. start thread to hand agent task
                this.startAgentTaskThread(request);
            } catch (Exception e) {
                log.error("Error in SSE stream: {}", e.getMessage(), e);
                return new JsonRpcResponse<>(request.getId(), new InternalError(e.getMessage()));
            }
            return null;
        });
    }

    private void startAgentTaskThread(SendTaskStreamingRequest request) {
        String id = request.getId();
        TaskSendParams params = request.getParams();
        String taskId = params.getId();
        String sessionId = params.getSessionId();
        String prompts = getUserQuery(params);

        // TODO simulate agent token stream and enqueue
        // keep multi turn conversation with sessionId
        Flux.range(1, 10)
                .subscribeOn(Schedulers.newSingle("echo-thread"))
                .subscribe(i -> {
                    List<Part> parts = Collections.singletonList(new TextPart("sse message: " + i));
                    TaskState state;
                    Message message = null;
                    Artifact artifact = null;
                    boolean finalFlag = false;
                    if (i == 10) {
                        state = TaskState.COMPLETED;
                        finalFlag = true;
                        artifact = new Artifact(parts, 0, false);
//                    } else if (i == 5) {
//                        state = TaskState.INPUT_REQUIRED;
//                        message = new Message(Role.AGENT, parts, null);
//                        finalFlag = true;
                    } else {
                        state = TaskState.WORKING;
                        message = new Message(Role.AGENT, parts, null);
                    }

                    TaskStatus taskStatus = new TaskStatus(state, message);
                    Task latestTask = this.updateStore(taskId, taskStatus, Collections.singletonList(artifact));
                    // send notification
                    this.sendTaskNotification(latestTask);

                    // artifact event
                    if (artifact != null) {
                        TaskArtifactUpdateEvent taskArtifactUpdateEvent = new TaskArtifactUpdateEvent(taskId, artifact);
                        this.enqueueEvent(taskId, taskArtifactUpdateEvent);
                    }

                    // status event
                    TaskStatusUpdateEvent taskStatusUpdateEvent = new TaskStatusUpdateEvent(taskId, taskStatus, finalFlag);
                    this.enqueueEvent(taskId, taskStatusUpdateEvent);
                });
    }

    private static String getUserQuery(TaskSendParams ps) {
        List<Part> parts = ps.getMessage().getParts();
        return parts.stream()
                .filter(p -> p instanceof TextPart)
                .map(p -> ((TextPart) p).getText())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public Mono<JsonRpcResponse<?>> onResubscribeTask(TaskResubscriptionRequest request) {
        TaskIdParams params = request.getParams();
        try {
            this.initEventQueue(params.getId(), true);
        } catch (Exception e) {
            log.error("Error while reconnecting to SSE stream: {}", e.getMessage());
            return Mono.just(new JsonRpcResponse<>(request.getId(), new InternalError("An error occurred while reconnecting to stream: " + e.getMessage())));
        }

        return Mono.empty();
    }

    // simulate agent invoke
    private Mono<List<Artifact>> agentInvoke(TaskSendParams ps) {
        List<Part> parts = ps.getMessage().getParts();
        String prompts = parts.stream()
                .filter(p -> p instanceof TextPart)
                .map(p -> ((TextPart) p).getText())
                .collect(Collectors.joining("\n"));

        return this.agent.chat(prompts).map(answer -> {
            Artifact artifact = Artifact.builder()
                    .name("echo")
                    .description("echo request")
                    .append(false)
                    .parts(Collections.singletonList(new TextPart(answer)))
                    .build();
            return Collections.singletonList(artifact);
        });
    }


    private JsonRpcResponse<Object> validRequest(JsonRpcRequest<TaskSendParams> request) {
        TaskSendParams ps = request.getParams();
        if (!Util.areModalitiesCompatible(ps.getAcceptedOutputModes(), supportModes)) {
            log.warn("Unsupported output mode. Received: {}, Support: {}",
                    ps.getAcceptedOutputModes(),
                    supportModes);
            return Util.newIncompatibleTypesError(request.getId());
        }

        if (ps.getPushNotification() != null && Util.isEmpty(ps.getPushNotification().getUrl())) {
            log.warn("Push notification URL is missing");
            return new JsonRpcResponse<>(request.getId(), new InvalidParamsError("Push notification URL is missing"));
        }
        return null;
    }
}

