package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import lombok.extern.slf4j.Slf4j;

/**
 * @author PheonixHkbxoic
 * @date 2025/6/3 22:15
 * @desc
 */
@Slf4j
public class McpContentInjector extends DefaultContentInjector {
    protected McpServersManager mcpServersManager;

    public McpContentInjector(McpServersManager mcpServersManager) {
        this.mcpServersManager = mcpServersManager;
    }

    @Override
    protected String format(Content content) {
        Metadata metadata = content.textSegment().metadata();
        String text = content.textSegment().text();
//        log.info("content metadata: {}", content.metadata());
//        log.info("tool metadata: {}, text: {}", metadata, text);
//        String fmt = super.format(content);
        return "text: " + text + "\n\nmetadata: " + Util.toJson(metadata.toMap());
    }
}
