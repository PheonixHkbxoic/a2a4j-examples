package io.github.pheonixhkbxoic.a2a4j.examples.agents.echoagent.core;


import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.AgentCapabilities;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.AgentCard;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 19:40
 * @desc
 */
@Configuration(proxyBeanMethods = false)
public class EchoConfig {
    @Value("${server.port}")
    private Integer port;

    @Bean
    public AgentCard agentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        AgentSkill skill = AgentSkill.builder()
                .id("echoAgent")
                .name("echo agent")
                .description("just echo user message")
                .tags(List.of("echo"))
                .examples(Collections.singletonList("I'm big strong!"))
                .inputModes(Collections.singletonList("text"))
                .outputModes(Collections.singletonList("text"))
                .build();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("echoAgent");
        agentCard.setDescription("echo agent, Answer the user's questions exactly as they are");
        agentCard.setUrl("http://127.0.0.1:" + port);
        agentCard.setVersion("2.0.2");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        return agentCard;
    }

}
