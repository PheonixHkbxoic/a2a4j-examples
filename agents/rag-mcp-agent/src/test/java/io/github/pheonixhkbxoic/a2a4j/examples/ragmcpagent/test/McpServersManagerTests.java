package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.test;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.McpServersManager;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author PheonixHkbxoic
 * @date 2025/6/2 22:22
 * @desc
 */
@Slf4j
public class McpServersManagerTests {
    static McpServersManager manager;
    static List<Document> documents;

    @BeforeAll
    public static void init() throws IOException {
        Map<String, String> cmdMap = Map.of("npx", "C:/Program Files/nodejs/npx.cmd");
        manager = new McpServersManager(null, cmdMap);

        // mock load mcpServers.json
        Path docPath = Path.of(new ClassPathResource("mcpServers.json").getURI());
        documents = manager.loadDocuments(docPath);
        documents.stream().map(Document::metadata).forEach(metadata -> log.info("tool: {}", metadata));
    }

    public void testCallTool(String mcpServerName, String mcpToolName, Map<String, Object> args) {
        // mock rag query
        Optional<Document> matched = documents.stream()
                .filter(doc -> {
                    Metadata metadata = doc.metadata();
                    String serverName = metadata.getString(McpServersManager.METADATA_KEY_mcpServerName);
                    String toolName = metadata.getString(McpServersManager.METADATA_KEY_mcpToolName);
                    return serverName != null && serverName.equalsIgnoreCase(mcpServerName)
                            && toolName != null && toolName.equalsIgnoreCase(mcpToolName);
                })
                .findFirst();
        Assertions.assertThat(matched).isPresent();
        Metadata metadata = matched.get().metadata();
        log.info("tool metadata: {}", metadata);

        // mock call tool
        McpSchema.CallToolResult result = manager.callTool(metadata, args);
        Assertions.assertThat(result).extracting(McpSchema.CallToolResult::getIsError).isEqualTo(false);
        log.info("tool result: {}", result.getContent());
    }

    @Test
    public void testFileSystem() {
        testCallTool("filesystem", "list_allowed_directories", Map.of("path", "D:/"));
        testCallTool("filesystem", "list_directory", Map.of("path", "D:/"));
        testCallTool("filesystem", "get_file_info", Map.of("path", "D:/pheonix"));
        // may be timeout if sum of files is too many
        testCallTool("filesystem", "directory_tree", Map.of("path", "D:\\pheonix\\desktopbg"));

    }


}
