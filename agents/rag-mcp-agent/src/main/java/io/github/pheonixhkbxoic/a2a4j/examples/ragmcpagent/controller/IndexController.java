package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.controller;

import dev.langchain4j.service.Result;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.Assistant;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.McpServerProperties;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.McpServersManager;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.RagResultTransformer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 23:33
 * @desc
 */
@Slf4j
@Controller
public class IndexController {

    @Resource
    McpServersManager mcpServersManager;

    @Resource
    Assistant assistant;

    @ResponseBody
    @GetMapping("/chat")
    public ResponseEntity<String> chat(String prompt) {
        try {
            Result<String> result = assistant.chat("1", prompt);
            result = RagResultTransformer.transform(mcpServersManager, result);
            return ResponseEntity.ok(result.content());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/ingest")
    public ResponseEntity<Object> ingest(Map<String, McpServerProperties> mcpServers) {
        try {
            mcpServersManager.ingest(mcpServers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
