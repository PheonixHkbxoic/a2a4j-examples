package io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.core;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.pheonixhkbxoic.a2a4j.core.core.AgentInvoker;
import io.github.pheonixhkbxoic.a2a4j.core.core.StreamData;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.SendTaskRequest;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.SendTaskStreamingRequest;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.tool.Calculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/14 15:34
 * @desc math agent: use LangChain4j low level  api
 */
@SuppressWarnings("unchecked")
@Component
@Slf4j
public class MathAgent implements AgentInvoker {
    private final ChatModel model;
    private final StreamingChatModel streamingModel;
    private final Calculator calculator = new Calculator();

    private final SystemMessage systemMessage = new SystemMessage("You are a math genius, good at resolving all math questions");

    public MathAgent(ChatModel model, StreamingChatModel streamingModel) {
        this.model = model;
        this.streamingModel = streamingModel;
    }

    // manually handle tool execution
    private final Map<String, Function<String, String>> toolHandlerMappings = Map.of(
            "squareRoot", args -> {
                Map<String, Object> params = Util.fromJson(args, HashMap.class);
                double x = Double.parseDouble(String.valueOf(params.get("x")));
                int precision = Integer.parseInt(String.valueOf(params.getOrDefault("precision", 8)));
                return calculator.squareRoot(x, precision);
            },
            "add", args -> {
                Map<String, Object> params = Util.fromJson(args, HashMap.class);
                int a = Integer.parseInt(String.valueOf(params.get("a")));
                int b = Integer.parseInt(String.valueOf(params.getOrDefault("b", 8)));
                return String.valueOf(calculator.add(a, b));
            }
    );

    Mono<List<Artifact>> chat(String sessionId, String prompt) {
        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, UserMessage.from(prompt))
                .toolSpecifications(ToolSpecifications.toolSpecificationsFrom(Calculator.class))
                .build();

        return Mono.fromSupplier(() -> {
            ChatResponse response = model.chat(request);
            AiMessage aiMessage = response.aiMessage();
            boolean toolExecuted = aiMessage.hasToolExecutionRequests();
            String text = aiMessage.text();
            if (Util.isEmpty(text)) {
                text = "";
            }
            if (toolExecuted) {
                String result = this.invokeTools(aiMessage);
                text = text + result;
            }
            log.info("question: {}, answer: {}, toolExecuted: {}", prompt, text, toolExecuted);
            return List.of(Artifact.builder().parts(List.of(new TextPart(text))).build());
        });
    }

    private String invokeTools(AiMessage aiMessage) {
        return aiMessage.toolExecutionRequests().stream()
                .map(r -> {
//                    Function<String, String> handler = toolHandlerMappings.get(r.name());
//                    if (handler == null) {
//                        return "";
//                    }
//                    return handler.apply(r.arguments());
                    try {
                        Map<String, Object> params = Util.fromJson(r.arguments(), HashMap.class);
                        Object result = ToolUtil.invoke(calculator, r.name(), params);
                        return result == null ? "" : result.toString();
                    } catch (Exception e) {
                        log.error("tool execute exception, name: {}, arguments: {}, error: {}",
                                r.name(), r.arguments(), e.getMessage(), e);
                    }
                    return "";
                })
                .collect(Collectors.joining("\n"));
    }

    public Flux<StreamData> chatStream(String sessionId, String prompt) {
        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, UserMessage.from(prompt))
                .toolSpecifications(ToolSpecifications.toolSpecificationsFrom(Calculator.class))
                .build();
        MathAgent that = this;

        return Flux.create(sink -> streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String text) {
                List<Part> parts = Collections.singletonList(new TextPart(text));
                Message message = new Message(Role.AGENT, parts, null);
                StreamData streamData = StreamData.builder()
                        .state(TaskState.WORKING)
                        .message(message)
                        .build();
                sink.next(streamData);
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                AiMessage aiMessage = chatResponse.aiMessage();
                if (aiMessage.hasToolExecutionRequests()) {
                    String result = that.invokeTools(aiMessage);
                    log.info("question: {}, tool result: {}", prompt, result);
                    List<Part> parts = Collections.singletonList(new TextPart(result));
                    Artifact artifact = Artifact.builder().name("mathAgent").parts(parts).build();
                    Message message = new Message(Role.AGENT, List.of(), null);
                    StreamData streamData = StreamData.builder()
                            .state(TaskState.COMPLETED)
                            .message(message)
                            .artifact(artifact)
                            .endStream(true)
                            .build();
                    sink.next(streamData);
                }
                sink.complete();
            }

            @Override
            public void onError(Throwable throwable) {
                sink.error(throwable);
            }
        }));
    }

    @Override
    public Mono<List<Artifact>> invoke(SendTaskRequest request) {
        TaskSendParams params = request.getParams();
        return this.chat(params.getSessionId(), this.extractUserQuery(params));
    }

    @Override
    public Flux<StreamData> invokeStream(SendTaskStreamingRequest request) {
        TaskSendParams params = request.getParams();
        return this.chatStream(params.getSessionId(), this.extractUserQuery(params));
    }
}
