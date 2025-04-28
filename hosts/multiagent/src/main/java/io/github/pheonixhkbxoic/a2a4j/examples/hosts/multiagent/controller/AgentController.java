package io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.controller;

import io.github.pheonixhkbxoic.a2a4j.core.spec.error.A2AClientHTTPError;
import io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.manager.AgentRouter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 22:27
 * @desc
 */
@Slf4j
@RestController
public class AgentController {
    @Resource
    private AgentRouter agentRouter;

    @GetMapping("/chat")
    public ResponseEntity<String> chat(@RequestParam("userId") String userId,
                                       @RequestParam("sessionId") String sessionId,
                                       @RequestParam("prompts") String prompts) {
        Mono<String> result = this.agentRouter.chat(userId, sessionId, prompts);
        String block = null;
        try {
            block = result.block();
        } catch (A2AClientHTTPError e) {
            log.error("chat status: {}, error: {}", e.getStatusCode(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } catch (Exception e) {
            log.error("chat error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
        return ResponseEntity.ok(block);
    }

    @GetMapping(value = "/completed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> completed(@RequestParam("userId") String userId,
                                  @RequestParam("sessionId") String sessionId,
                                  @RequestParam("prompts") String prompts) {
        return this.agentRouter.chatStream(userId, sessionId, prompts);
    }

}
