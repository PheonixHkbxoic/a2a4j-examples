package io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.controller;

import io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.manager.AgentRouter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    public Mono<String> chat(@RequestParam("userId") String userId,
                             @RequestParam("sessionId") String sessionId,
                             @RequestParam("prompts") String prompts) {
        return this.agentRouter.chat(userId, sessionId, prompts);
    }

    @GetMapping(value = "/completed", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> completed(@RequestParam("userId") String userId,
                                  @RequestParam("sessionId") String sessionId,
                                  @RequestParam("prompts") String prompts) {
        return this.agentRouter.chatStream(userId, sessionId, prompts);
    }

}
