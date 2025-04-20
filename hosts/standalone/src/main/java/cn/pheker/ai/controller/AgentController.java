package cn.pheker.ai.controller;

import cn.pheker.ai.a2a4j.core.client.A2AClient;
import cn.pheker.ai.a2a4j.core.spec.entity.*;
import cn.pheker.ai.a2a4j.core.spec.error.JsonRpcError;
import cn.pheker.ai.a2a4j.core.spec.message.SendTaskResponse;
import cn.pheker.ai.a2a4j.core.spec.message.SendTaskStreamingResponse;
import cn.pheker.ai.a2a4j.core.util.Util;
import cn.pheker.ai.a2a4j.core.util.Uuid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
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
    private List<A2AClient> clients;


    @GetMapping("/chat")
    public ResponseEntity<Object> chat(String userId, String sessionId, String prompts) {
        A2AClient client = clients.get(0);
        TaskSendParams params = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .historyLength(3)
                .acceptedOutputModes(Collections.singletonList("text"))
                .message(new Message(Role.USER, Collections.singletonList(new TextPart(prompts)), null))
                .pushNotification(client.getPushNotificationConfig())
                .build();
        log.info("params: {}", Util.toJson(params));
        SendTaskResponse sendTaskResponse = client.sendTask(params);

        JsonRpcError error = sendTaskResponse.getError();
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }
        Task task = sendTaskResponse.getResult();
        String answer = task.getArtifacts().stream()
                .flatMap(t -> t.getParts().stream())
                .filter(p -> new TextPart().getType().equals(p.getType()))
                .map(p -> ((TextPart) p).getText())
                .collect(Collectors.joining("\n"));
        return ResponseEntity.ok(answer);
    }

    @GetMapping(value = "/completed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter completed(String userId, String sessionId, String prompts) {
        log.info("user qa start: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        SseEmitter sseEmitter = new SseEmitter(5 * 1000L);
        sseEmitter.onTimeout(() -> log.info("user sse timeout"));
        sseEmitter.onError(e -> log.error("user sse error: {}", e.getMessage(), e));
        sseEmitter.onCompletion(() -> log.info("user qa completed: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        A2AClient client = clients.get(0);
        TaskSendParams params = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .historyLength(3)
                .acceptedOutputModes(Collections.singletonList("text"))
                .message(new Message(Role.USER, Collections.singletonList(new TextPart(prompts)), null))
                .pushNotification(client.getPushNotificationConfig())
                .build();
        log.info("params: {}", Util.toJson(params));
        Flux<SendTaskStreamingResponse> responseFlux = client.sendTaskSubscribe(params);

        responseFlux
                .publishOn(Schedulers.boundedElastic())
                .flatMap(r -> {
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
                .filter(p -> new TextPart().getType().equals(p.getType()))
                .map(p -> ((TextPart) p).getText())
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
