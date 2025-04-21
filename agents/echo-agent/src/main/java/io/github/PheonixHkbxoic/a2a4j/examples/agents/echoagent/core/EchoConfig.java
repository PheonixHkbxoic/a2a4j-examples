package io.github.PheonixHkbxoic.a2a4j.examples.agents.echoagent.core;


import io.github.PheonixHkbxoic.a2a4j.core.spec.entity.AgentCapabilities;
import io.github.PheonixHkbxoic.a2a4j.core.spec.entity.AgentCard;
import io.github.PheonixHkbxoic.a2a4j.core.spec.entity.AgentSkill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 19:40
 * @desc
 */
@Configuration
public class EchoConfig {
    @Value("${server.port}")
    private Integer port;

    @Bean
    public AgentCard agentCard() {
        AgentCapabilities capabilities = new AgentCapabilities();
        AgentSkill skill = AgentSkill.builder().id("convert_currency").name("Currency Exchange Rates Tool").description("Helps with exchange values between various currencies").tags(Arrays.asList("currency conversion", "currency exchange")).examples(Collections.singletonList("What is exchange rate between USD and GBP?")).inputModes(Collections.singletonList("text")).outputModes(Collections.singletonList("text")).build();
        AgentCard agentCard = new AgentCard();
        agentCard.setName("Currency Agent");
        agentCard.setDescription("current exchange");
        agentCard.setUrl("http://127.0.0.1:" + port);
        agentCard.setVersion("1.0.0");
        agentCard.setCapabilities(capabilities);
        agentCard.setSkills(Collections.singletonList(skill));
        return agentCard;
    }


}
