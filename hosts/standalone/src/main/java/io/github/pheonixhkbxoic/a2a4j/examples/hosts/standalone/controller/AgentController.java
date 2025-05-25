package io.github.pheonixhkbxoic.a2a4j.examples.hosts.standalone.controller;

import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClient;
import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClientSet;
import io.github.pheonixhkbxoic.a2a4j.core.spec.ValueError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.error.A2AClientHTTPError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.SendTaskStreamingResponse;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import io.github.pheonixhkbxoic.a2a4j.core.util.Uuid;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 22:27
 * @desc
 */
@Slf4j
@RestController
public class AgentController {
    @Resource
    private A2AClientSet clientSet;


    @GetMapping("/chat")
    public ResponseEntity<Object> chat(String userId, String sessionId, String prompts) {
        A2AClient client = clientSet.getByConfigKey("echoAgent");
        TaskSendParams params = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .historyLength(3)
                .acceptedOutputModes(Collections.singletonList("text"))
                .message(new Message(Role.USER, Collections.singletonList(new TextPart(prompts)), null))
                .pushNotification(client.getPushNotificationConfig())
                .build();
        log.info("chat params: {}", Util.toJson(params));
        try {
            String answer = client.sendTask(params)
                    .flatMap(sendTaskResponse -> {
                        if (sendTaskResponse.getError() != null) {
                            return Mono.error(new ValueError(Util.toJson(sendTaskResponse.getError())));
                        }
                        Task task = sendTaskResponse.getResult();
                        return Mono.just(task.getArtifacts().stream()
                                .flatMap(t -> t.getParts().stream())
                                .filter(p -> Part.TEXT.equals(p.getType()))
                                .map(p -> ((TextPart) p).getText())
                                .filter(t -> !Util.isEmpty(t))
                                .collect(Collectors.joining("\n")));
                    })
                    .block();
            return ResponseEntity.ok(answer);
        } catch (A2AClientHTTPError e) {
            log.error("chat exception: {}", e.getMessage(), e);
            return ResponseEntity.status(e.getStatusCode()).body(e.getMessage());
        } catch (Exception e) {
            log.error("chat exception: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping(value = "/completed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter completed(String userId, String sessionId, String prompts) {
        log.info("user qa start: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        SseEmitter sseEmitter = new SseEmitter(5 * 1000L);
        sseEmitter.onTimeout(() -> log.info("user sse timeout"));
        sseEmitter.onError(e -> log.error("user sse error: {}", e.getMessage(), e));
        sseEmitter.onCompletion(() -> log.info("user qa completed: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        A2AClient client = clientSet.getByConfigKey("echoAgent");
        TaskSendParams params = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .historyLength(3)
                .acceptedOutputModes(Collections.singletonList("text"))
                .message(new Message(Role.USER, Collections.singletonList(new TextPart(prompts)), null))
                .pushNotification(client.getPushNotificationConfig())
                .build();
        log.info("completed params: {}", Util.toJson(params));
        Flux<SendTaskStreamingResponse> responseFlux = client.sendTaskSubscribe(params);

        responseFlux
                .publishOn(Schedulers.boundedElastic())
                .flatMap(r -> {
                    if (r.getError() != null) {
                        return Flux.error(new ValueError(Util.toJson(r.getError())));
                    }
                    UpdateEvent event = r.getResult();
                    if (event instanceof TaskStatusUpdateEvent) {
                        Message message = ((TaskStatusUpdateEvent) event).getStatus().getMessage();
                        if (message == null) {
                            return Flux.empty();
                        }
                        return Flux.fromStream(message.getParts().stream());
                    }
                    Artifact artifact = ((TaskArtifactUpdateEvent) r.getResult()).getArtifact();
                    return Flux.fromStream(artifact.getParts().stream());
                })
                .filter(p -> Part.TEXT.equals(p.getType()))
                .map(p -> ((TextPart) p).getText())
                .filter(t -> !Util.isEmpty(t))
                .doOnNext(s -> log.info("client received: {}", s))
                .doOnComplete(sseEmitter::complete)
                .doOnError(sseEmitter::completeWithError)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(t -> {
                    try {
                        sseEmitter.send(t);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                        throw new RuntimeException(e);
                    }
                });
        return sseEmitter;
    }

}
