package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/27 20:32
 * @desc
 */
public interface Assistant {


    Result<String> chat(@MemoryId String memoryId, @UserMessage String userMessage);

    Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String userMessage);

}
