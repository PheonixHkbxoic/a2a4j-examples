package io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.core;


import io.github.pheonixhkbxoic.a2a4j.core.core.InMemoryTaskManager;
import io.github.pheonixhkbxoic.a2a4j.core.core.PushNotificationSenderAuth;
import io.github.pheonixhkbxoic.a2a4j.core.core.TaskStore;
import io.github.pheonixhkbxoic.a2a4j.core.spec.ValueError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.error.InternalError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.error.InvalidParamsError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.*;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import lombok.extern.slf4j.Slf4j;
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
public class MathTaskManager extends InMemoryTaskManager {
    // wire agent
    private final MathAgent mathAgent;
    // agent support modes
    private final List<String> supportModes = Arrays.asList("text", "file", "data");

    public MathTaskManager(TaskStore taskStore,
                           PushNotificationSenderAuth pushNotificationSenderAuth,
                           MathAgent mathAgent) {
        super(taskStore, pushNotificationSenderAuth);
        this.mathAgent = mathAgent;
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
            boolean verified = this.verifyPushNotificationInfo(ps.getPushNotification());
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
                    boolean verified = this.verifyPushNotificationInfo(ps.getPushNotification());
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

        // keep multi turn conversation with sessionId
        mathAgent.chatStream(sessionId, prompts)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> {
                    // end stream
                    List<Part> parts = Collections.singletonList(new TextPart(e.getMessage()));
                    Message message = new Message(Role.AGENT, parts, null);
                    TaskStatus taskStatus = new TaskStatus(TaskState.FAILED, message);

                    TaskStatusUpdateEvent taskStatusUpdateEvent = new TaskStatusUpdateEvent(taskId, taskStatus, true);
                    this.enqueueEvent(taskId, taskStatusUpdateEvent);
                })
                .subscribe(taskStatus -> {
                    Task latestTask = this.updateStore(taskId, taskStatus, Collections.singletonList(null));
                    // send notification
                    this.sendTaskNotification(latestTask);

                    // end sse stream
                    boolean finalFlag = !TaskState.WORKING.equals(taskStatus.getState());
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

        return this.mathAgent.chat(ps.getSessionId(), prompts).map(answer -> {
            Artifact artifact = Artifact.builder()
                    .name("math_answer")
                    .description("answer of math question")
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

