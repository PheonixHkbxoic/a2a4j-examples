package io.github.pheonixhkbxoic.a2a4j.examples.ragagent.core;


import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.AgentCapabilities;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.AgentCard;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 19:40
 * @desc
 */
@Configuration
public class RagConfig {
    @Value("${server.port}")
    private Integer port;

    @Bean
    public AgentCard agentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        AgentSkill skill = AgentSkill.builder()
                .id("ragAgent")
                .name("rag agent")
                .description("Miles of Smiles Car Rental Services Terms of Use")
                .inputModes(Collections.singletonList("text"))
                .outputModes(Collections.singletonList("text"))
                .build();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("ragAgent");
        agentCard.setDescription("Miles of Smiles Car Rental Services Terms of Use");
        agentCard.setUrl("http://127.0.0.1:" + port);
        agentCard.setVersion("2.0.2");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        return agentCard;
    }


}
