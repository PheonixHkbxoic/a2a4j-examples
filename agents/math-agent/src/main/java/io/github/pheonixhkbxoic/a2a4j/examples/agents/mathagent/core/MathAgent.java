package io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.core;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.tool.Calculator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/14 15:34
 * @desc math agent: use LangChain4j low level  api
 */
@Slf4j
@Component
public class MathAgent {
    @Resource
    private ChatLanguageModel model;
    @Resource
    private StreamingChatLanguageModel streamingModel;
    private final Calculator calculator = new Calculator();

    private final SystemMessage systemMessage = new SystemMessage("You are a math genius, good at resolving all math questions");
    private final Map<String, Function<String, String>> toolHandlerMappings = Map.of(
            "squareRoot", args -> {
                Map params = Util.fromJson(args, HashMap.class);
                double x = Double.parseDouble(String.valueOf(params.get("x")));
                int precision = Integer.parseInt(String.valueOf(params.getOrDefault("precision", 8)));
                return calculator.squareRoot(x, precision);
            },
            "add", args -> {
                Map params = Util.fromJson(args, HashMap.class);
                int a = Integer.parseInt(String.valueOf(params.get("a")));
                int b = Integer.parseInt(String.valueOf(params.getOrDefault("b", 8)));
                return String.valueOf(calculator.add(a, b));
            }
    );

    Mono<String> chat(String prompt) {
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
            return text;
        });
    }

    private String invokeTools(AiMessage aiMessage) {
        return aiMessage.toolExecutionRequests().stream()
                .map(r -> {
                    Function<String, String> handler = toolHandlerMappings.get(r.name());
                    if (handler == null) {
                        return "";
                    }
                    try {
                        return handler.apply(r.arguments());
                    } catch (Exception e) {
                        log.error("tool execute exception, name: {}, arguments: {}, error: {}",
                                r.name(), r.arguments(), e.getMessage(), e);
                    }
                    return "";
                })
                .collect(Collectors.joining("\n"));
    }

    // TODO streamModel return "" if use toolSpecifications
    public Flux<String> chatStream(String prompt) {
        ChatRequest request = ChatRequest.builder()
                .messages(systemMessage, UserMessage.from(prompt))
                .toolSpecifications(ToolSpecifications.toolSpecificationsFrom(Calculator.class))
                .build();

        return Flux.create(sink -> streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String s) {
                sink.next(s);
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                sink.complete();
            }

            @Override
            public void onError(Throwable throwable) {
                sink.error(throwable);
            }
        }));
    }

}
