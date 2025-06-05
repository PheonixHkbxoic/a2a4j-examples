package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.Assistant;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.McpContentInjector;
import io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core.McpServersManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.time.Duration.ofSeconds;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/26 15:54
 * @desc
 */
@Configuration
public class LangChainConfig {

    @Bean
    ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-chat")
                .baseUrl("https://api.deepseek.com")
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-chat")
                .baseUrl("https://api.deepseek.com")
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    TokenCountEstimator tokenCountEstimator() {
        return new OpenAiTokenCountEstimator(GPT_4_O_MINI);
    }

    @Bean
    ChatMemoryProvider chatMemoryProvider(TokenCountEstimator tokenCountEstimator) {
        return memoryId -> TokenWindowChatMemory.builder()
                .id(memoryId)
                .maxTokens(5000, tokenCountEstimator)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore() {
//        return new InMemoryEmbeddingStore<>();

        return MilvusEmbeddingStore.builder()
                .uri("http://localhost:19530")
                .token("root:Milvus")
                .collectionName("rag_mcp_tool")
                .dimension(384)
                .build();
    }


    @Bean
    EmbeddingModel embeddingModel() {
        return new BgeSmallEnV15QuantizedEmbeddingModel();
    }

    @Bean
    EmbeddingStoreIngestor embeddingStoreIngestor(EmbeddingModel embeddingModel,
                                                  EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.75)
                .build();
    }

    @Bean
    McpServersManager mcpServersManager(EmbeddingStoreIngestor embeddingStoreIngestor) {
        Map<String, String> cmdMap = Map.of(
                "npx", "C:/Program Files/nodejs/npx.cmd",
                "npm", "C:/Program Files/nodejs/npm.cmd",
                "python", "D:/miniconda3/python.exe",
                "uv", "D:/miniconda3/Scripts/uv.exe",
                "go", "C:/Program Files/Go/bin/go.exe"
        );
        McpServersManager mcpServersManager = new McpServersManager(embeddingStoreIngestor, cmdMap);
        Path docPath = null;
        try {
            docPath = Path.of(new ClassPathResource("mcpServers.json").getURI());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mcpServersManager.ingest(docPath);
        return mcpServersManager;
    }

    @Bean
    ContentInjector mcpContentInjector(McpServersManager mcpServersManager) {
        return new McpContentInjector(mcpServersManager);
    }

    @Bean
    RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever, ContentInjector mcpContentInjector) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentInjector(mcpContentInjector)
                .build();
    }

    @Bean
    Assistant assistant(ChatMemoryProvider chatMemoryProvider, RetrievalAugmentor retrievalAugmentor) {
        return AiServices.builder(Assistant.class)
                .chatMemoryProvider(chatMemoryProvider)
                .chatModel(chatModel())
                .streamingChatModel(streamingChatModel())
                .retrievalAugmentor(retrievalAugmentor)
                .systemMessageProvider(nop -> """
                        mcp rag agent, function tools is available. if tool found, please return json ```
                        {
                          "mcpServerName": "mcpServerName in metadata",
                          "mcpToolName": "mcpToolName in metadata",
                          "args": "tool args, it's the type of Map<String, Object>"
                        }
                        ```
                        The args must obey tool inputSchema
                        """)
                .build();
    }

}
