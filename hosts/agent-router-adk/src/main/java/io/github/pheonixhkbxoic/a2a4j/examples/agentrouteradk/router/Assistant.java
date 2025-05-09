package io.github.pheonixhkbxoic.a2a4j.examples.agentrouteradk.router;

import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClient;
import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClientSet;
import io.github.pheonixhkbxoic.a2a4j.host.autoconfiguration.A2a4jAgentsProperties;
import io.github.pheonixhkbxoic.adk.AdkAgentProvider;
import io.github.pheonixhkbxoic.adk.Payload;
import io.github.pheonixhkbxoic.adk.core.edge.DefaultRouterSelector;
import io.github.pheonixhkbxoic.adk.event.InMemoryEventService;
import io.github.pheonixhkbxoic.adk.runner.AgentRouterRunner;
import io.github.pheonixhkbxoic.adk.runtime.*;
import io.github.pheonixhkbxoic.adk.session.InMemorySessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static io.github.pheonixhkbxoic.a2a4j.examples.agentrouteradk.router.RouterAgent.ACTIVE_AGENT;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/26 15:45
 * @desc agent router
 */
@Component
@Slf4j
public class Assistant {
    public static final String APP_NAME = "Assistant";
    private final A2a4jAgentsProperties a2a4jAgentsProperties;
    private final RouterAgent routerAgent;
    private final A2AClientSet clientSet;
    private final Executor executor = new Executor(new InMemorySessionService(), new InMemoryEventService());
    private final AgentRouterRunner runner;

    public Assistant(A2a4jAgentsProperties a2a4jAgentsProperties, RouterAgent routerAgent, A2AClientSet clientSet) {
        this.a2a4jAgentsProperties = a2a4jAgentsProperties;
        this.routerAgent = routerAgent;
        this.clientSet = clientSet;
        this.runner = this.buildRunner();
    }

    public Mono<Map<String, Object>> chat(Payload payload) {
        return Mono.fromSupplier(() -> {
            String message = this.runner.run(payload).getMessage();
            return Map.of("answer", message, "payload", payload);
        });
    }

    public Flux<Map<String, Object>> chatStream(Payload payload) {
        return this.runner.runAsync(payload).map(responseFrame -> {
            String message = responseFrame.getMessage();
            return Map.of("answer", message, "payload", payload);
        });
    }

    public ByteArrayResource generatePng(Payload payload) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            this.runner.generateTaskPng(payload, baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ByteArrayResource(baos.toByteArray());
    }

    private AgentRouterRunner buildRunner() {
        AdkAgentProvider routerAgent = AdkAgentProvider.create("routerAgent", this.routerAgent);

        // route to RemoteAgent
        BranchSelector branchSelector = new DefaultRouterSelector(ACTIVE_AGENT);
        AdkAgentProvider[] agentProviderList = clientSet.keySet().stream()
                .map(key -> {
                    A2AClient client = clientSet.getByConfigKey(key);
                    return AdkAgentProvider.create(client.getAgentCard().getName(), new RemoteAgent(client, a2a4jAgentsProperties));
                })
                .toArray(AdkAgentProvider[]::new);

        // fallback when no suitable RemoteAgent
        AdkAgentProvider fallback = AdkAgentProvider.create("fallback", new AdkAgentInvoker() {
            @Override
            public Mono<ResponseFrame> invoke(ExecutableContext context) {
                String answer = (String) context.getMetadata().get("answer");
                log.info("fallback: {}", answer);
                ResponseFrame response = new ResponseFrame();
                response.setMessage(answer);
                return Mono.just(response);
            }

            @Override
            public Flux<ResponseFrame> invokeStream(ExecutableContext context) {
                return this.invoke(context).flux();
            }
        });
        return AgentRouterRunner.of(APP_NAME, routerAgent, branchSelector, fallback, agentProviderList)
                .initExecutor(executor);
    }


}
