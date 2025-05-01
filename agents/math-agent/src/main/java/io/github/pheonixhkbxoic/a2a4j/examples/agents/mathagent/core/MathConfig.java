package io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.core;


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
@Configuration
public class MathConfig {
    @Value("${server.port}")
    private Integer port;

    @Bean
    public AgentCard agentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        AgentSkill skill = AgentSkill.builder()
                .id("mathAgent")
                .name("math agent")
                .description("math genius, math expert, good at resolving all math questions")
                .tags(List.of("math genius", "math expert", "calculator"))
                .examples(Collections.singletonList("calculate square root of 13, keep 8 precision"))
                .inputModes(Collections.singletonList("text"))
                .outputModes(Collections.singletonList("text"))
                .build();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("mathAgent");
        agentCard.setDescription("math genius, math expert, good at resolving all math questions");
        agentCard.setUrl("http://127.0.0.1:" + port);
        agentCard.setVersion("2.0.0");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        return agentCard;
    }


}
