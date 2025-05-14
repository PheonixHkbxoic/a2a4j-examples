package io.github.pheonixhkbxoic.a2a4j.examples.agentrouteradk.controller;

import io.github.pheonixhkbxoic.a2a4j.core.spec.error.A2AClientHTTPError;
import io.github.pheonixhkbxoic.a2a4j.core.util.Uuid;
import io.github.pheonixhkbxoic.a2a4j.examples.agentrouteradk.router.Assistant;
import io.github.pheonixhkbxoic.adk.message.AdkPayload;
import io.github.pheonixhkbxoic.adk.message.AdkTextMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 22:27
 * @desc
 */
@Slf4j
@Controller
public class AssistantController {
    @Resource
    private Assistant assistant;

    @ResponseBody
    @GetMapping("/chat")
    public ResponseEntity<String> chat(@RequestParam("userId") String userId,
                                       @RequestParam("sessionId") String sessionId,
                                       @RequestParam("prompts") String prompts) {
        AdkPayload payload = AdkPayload.builder()
                .userId(userId)
                .sessionId(sessionId)
                .taskId(Uuid.uuid4hex())
                .messages(List.of(AdkTextMessage.of(prompts)))
                .build();
        Mono<String> result = this.assistant.chat(payload);
        try {
            String data = result.block();
            return ResponseEntity.ok(data);
        } catch (A2AClientHTTPError e) {
            log.error("chat status: {}, error: {}", e.getStatusCode(), e.getMessage(), e);
            return ResponseEntity.status(e.getStatusCode()).body(e.getMessage());
        } catch (Exception e) {
            log.error("chat error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping(value = "/completed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> completed(@RequestParam("userId") String userId,
                                  @RequestParam("sessionId") String sessionId,
                                  @RequestParam("prompts") String prompts) {
        AdkPayload payload = AdkPayload.builder()
                .userId(userId)
                .sessionId(sessionId)
                .taskId(Uuid.uuid4hex())
                .messages(List.of(AdkTextMessage.of(prompts)))
                .stream(true)
                .build();
        return this.assistant.chatStream(payload);
    }


    @GetMapping(value = "/generatePng", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<ByteArrayResource> generatePng(@RequestParam("userId") String userId,
                                                         @RequestParam("sessionId") String sessionId,
                                                         @RequestParam("taskId") String taskId) {
        AdkPayload payload = AdkPayload.builder()
                .userId(userId)
                .sessionId(sessionId)
                .taskId(taskId)
                .build();
        ByteArrayResource resource = this.assistant.generatePng(payload);

        HttpHeaders headers = new HttpHeaders();
        String filename = "agent_router_" + taskId + ".png";
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        // 构建响应实体并返回
        return ResponseEntity
                .status(HttpStatus.OK)
                .headers(headers)
                .body(resource);
    }
}
