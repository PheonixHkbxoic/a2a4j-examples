package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core;

import dev.langchain4j.service.Result;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/6/4 23:15
 * @desc
 */
@SuppressWarnings("unchecked")
public class RagResultTransformer {

    public static Result<String> transform(McpServersManager mcpServersManager, Result<String> result) {
        if (!Util.isEmpty(result.content())) {
            String toolJson = result.content().replace("```json", "").replaceFirst("```$", "");
            Map<String, Object> metadata = Util.fromJson(toolJson, HashMap.class);
            McpSchema.CallToolResult callToolResult = mcpServersManager.callTool(metadata);
            if (!callToolResult.getIsError()) {
                String toolResult = callToolResult.getContent().stream()
                        .map(c -> {
                            if (c instanceof McpSchema.TextContent) {
                                String text = ((McpSchema.TextContent) c).getText();
                                return text.replaceAll("\n", "<br>");
                            }
                            return c.toString();
                        })
                        .collect(Collectors.joining("\n"));
                return Result.<String>builder()
                        .content(toolResult)
                        .finishReason(result.finishReason())
                        .sources(result.sources())
                        .tokenUsage(result.tokenUsage())
                        .toolExecutions(result.toolExecutions())
                        .build();
            }
        }
        return result;
    }

}
