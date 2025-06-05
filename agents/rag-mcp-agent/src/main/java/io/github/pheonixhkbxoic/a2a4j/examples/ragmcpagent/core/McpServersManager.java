package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.source.FileSystemSource;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author PheonixHkbxoic
 * @date 2025/6/2 19:08
 * @desc
 */
@Slf4j
public class McpServersManager {

    public static String METADATA_KEY_mcpServerName = "mcpServerName";
    public static String METADATA_KEY_mcpToolName = "mcpToolName";

    private EmbeddingStoreIngestor embeddingStoreIngestor;
    private Map<String, String> commandPathMap = new HashMap<>();
    private final Map<String, McpServerProperties> mcpServers = new HashMap<>();
    private final LRUMap<String, McpClientProvider> mcpClientMap = new LRUMap<>();

    public McpServersManager() {
    }

    public McpServersManager(EmbeddingStoreIngestor embeddingStoreIngestor) {
        this.embeddingStoreIngestor = embeddingStoreIngestor;
    }

    public McpServersManager(EmbeddingStoreIngestor embeddingStoreIngestor, Map<String, String> commandPathMap) {
        this.embeddingStoreIngestor = embeddingStoreIngestor;
        this.commandPathMap = commandPathMap;
    }

    public void ingest(Path docPath) {
        List<Document> docs = this.loadDocuments(docPath);
        if (!docs.isEmpty()) {
            this.embeddingStoreIngestor.ingest(docs);
        }
    }

    public void ingest(Map<String, McpServerProperties> mcpServers) {
        mcpServers.forEach((mcpServerName, mcpServerProperties) -> {
            List<Document> docs = this.addMcpServer(mcpServerName, mcpServerProperties);
            if (!docs.isEmpty()) {
                this.embeddingStoreIngestor.ingest(docs);
            }
        });
    }

    public List<Document> loadDocuments(Path jsonPath) {
        FileSystemSource source = FileSystemSource.from(jsonPath);
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Map<String, McpServerProperties>> config = om.readValue(source.inputStream(), new TypeReference<>() {
            });
            Map<String, McpServerProperties> mcpServers = config.getOrDefault("mcpServers", new HashMap<>());
            return mcpServers.entrySet().stream()
                    .flatMap(entry -> this.addMcpServer(entry.getKey(), entry.getValue()).stream())
                    .toList();
        } catch (BlankDocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Document> addMcpServer(String mcpServerName, McpServerProperties mcpServerProperties) {
        if (mcpServers.containsKey(mcpServerName.toLowerCase())) {
            log.warn("addMcpServer mcp has loaded, ignored: {}", mcpServerName);
            return List.of();
        }
        log.info("addMcpServer loading mcp: {}", mcpServerName);
        mcpServers.put(mcpServerName.toLowerCase(), mcpServerProperties);
        if (mcpServerProperties.isDisabled()) {
            return List.of();
        }
        McpClientProvider mcpClientProvider = this.queryOrNew(mcpServerName.toLowerCase());
        Collection<FunctionTool> tools = mcpClientProvider.getTools();
        return tools.stream()
                .map(tool -> {
                    Document doc = Document.from(tool.description());
                    doc.metadata().put(METADATA_KEY_mcpServerName, mcpServerName.toLowerCase());
                    doc.metadata().put(METADATA_KEY_mcpToolName, tool.name());
                    doc.metadata().put("type", tool.type());
                    if (tool.inputSchema() != null) {
                        doc.metadata().put("inputSchema", tool.inputSchema());
                    }
                    if (tool.outputSchema() != null) {
                        doc.metadata().put("outputSchema", tool.outputSchema());
                    }
                    return doc;
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public McpSchema.CallToolResult callTool(Map<String, Object> data) {
        return this.callTool(data.getOrDefault(METADATA_KEY_mcpServerName, "").toString(),
                data.getOrDefault(METADATA_KEY_mcpToolName, "").toString(),
                (Map<String, Object>) data.getOrDefault("args", new HashMap<String, Object>()));
    }

    public McpSchema.CallToolResult callTool(Metadata docMetadata, Map<String, Object> args) {
        return this.callTool(docMetadata.getString(METADATA_KEY_mcpServerName),
                docMetadata.getString(METADATA_KEY_mcpToolName),
                args);
    }

    public McpSchema.CallToolResult callTool(String mcpServerName, String toolName, Map<String, Object> args) {
        if (mcpServerName == null || !mcpServers.containsKey(mcpServerName.toLowerCase())) {
            log.warn("callTool not found mcp server: {}", mcpServerName);
            return new McpSchema.CallToolResult(List.of(), true);
        }

        McpClientProvider mcpClientProvider = this.queryOrNew(mcpServerName.toLowerCase());
        McpSchema.CallToolResult result;
        try {
            result = mcpClientProvider.callTool(toolName, args);
            if (result.getIsError() == null) {
                result.setIsError(false);
            }
        } catch (Exception e) {
            log.error("callTool exception: {}, mcpServerName: {}, toolName: {}, args: {}", e.getMessage(), mcpServerName, toolName, args);
            result = new McpSchema.CallToolResult(List.of(), true);
        }
        return result;
    }

    public McpClientProvider queryOrNew(String mcpServerName) {
        return mcpClientMap.computeIfAbsent(mcpServerName, k -> {
            McpServerProperties mcpServerProperties = mcpServers.get(k);
            if (mcpServerProperties == null) {
                return null;
            }
            String sseUrl = mcpServerProperties.getUrl();
            String channel = McpChannel.STDIO;
            if (sseUrl != null && !sseUrl.isBlank()) {
                channel = McpChannel.SSE;
            }
            return McpClientProvider.builder()
                    .channel(channel)
                    .serverParameters(mcpServerProperties.toMcpServerParameters(commandPathMap))
                    .requestTimeout(Duration.ofSeconds(3))
                    .build();
        });
    }
}
