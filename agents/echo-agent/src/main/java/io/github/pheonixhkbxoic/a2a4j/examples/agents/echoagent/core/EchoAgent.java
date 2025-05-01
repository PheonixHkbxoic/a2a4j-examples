package io.github.pheonixhkbxoic.a2a4j.examples.agents.echoagent.core;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/14 15:34
 * @desc
 */
@Component
public class EchoAgent {

    Mono<String> chat(String prompt) {
        return Mono.just("I'm echo agent! echo: " + prompt);
    }

    Flux<String> chatStream(String prompt) {
        return chat(prompt).flux().flatMap(s -> {
            Stream<String> stream = Arrays.stream(s.split("(?=\\b)"));
            return Flux.fromStream(stream);
        });
    }

}
