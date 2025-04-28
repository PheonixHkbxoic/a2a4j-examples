package io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClientSet;
import io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.manager.AgentRouter;
import io.github.pheonixhkbxoic.a2a4j.host.autoconfiguration.A2a4jAgentsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/26 15:50
 * @desc
 */
@Configuration
public class AgentManagerConfig {

    @Bean
    public AgentRouter agentManager(A2AClientSet a2aClientSet,
                                    ChatLanguageModel model,
                                    StreamingChatLanguageModel streamingModel,
                                    A2a4jAgentsProperties a2a4jAgentsProperties) {
        return new AgentRouter(a2aClientSet, a2a4jAgentsProperties, model, streamingModel);
    }
}
