package io.github.pheonixhkbxoic.a2a4j.examples.ragagent.core;

import dev.langchain4j.service.Result;
import io.github.pheonixhkbxoic.a2a4j.core.core.AgentInvoker;
import io.github.pheonixhkbxoic.a2a4j.core.core.StreamData;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.SendTaskRequest;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.SendTaskStreamingRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author PheonixHkbxoic
 * @date 2025/5/1 01:35
 * @desc
 */
@Component
public class RagAgentInvoker implements AgentInvoker {
    @Resource
    private Assistant assistant;

    @Override
    public Mono<List<Artifact>> invoke(SendTaskRequest request) {
        String sessionId = request.getParams().getSessionId();
        String userQuery = this.extractUserQuery(request.getParams());
        return Mono.fromSupplier(() -> {
            Result<String> result = assistant.chat(sessionId, userQuery);
            Artifact artifact = Artifact.builder()
                    .name("math_answer")
                    .description("answer of math question")
                    .append(false)
                    .parts(Collections.singletonList(new TextPart(result.content())))
                    .build();
            return Collections.singletonList(artifact);
        });
    }

    @Override
    public Flux<StreamData> invokeStream(SendTaskStreamingRequest request) {
        String sessionId = request.getParams().getSessionId();
        String userQuery = this.extractUserQuery(request.getParams());

        return assistant.chatStream(sessionId, userQuery)
                .map(text -> {
                    Message message = Message.builder().role(Role.AGENT).parts(List.of(new TextPart(text))).build();
                    return StreamData.builder().state(TaskState.WORKING).message(message).endStream(false).build();
                })
                .concatWithValues(StreamData.builder()
                        .state(TaskState.COMPLETED)
                        .message(Message.builder().role(Role.AGENT).parts(List.of(new TextPart(""))).build())
                        .endStream(true)
                        .build());
    }

    @Override
    public List<String> acceptOutputModes() {
        return Arrays.asList("text", "file", "data");
    }
}
