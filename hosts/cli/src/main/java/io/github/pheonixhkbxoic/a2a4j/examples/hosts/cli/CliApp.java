package io.github.pheonixhkbxoic.a2a4j.examples.hosts.cli;

import io.github.pheonixhkbxoic.a2a4j.core.client.A2AClient;
import io.github.pheonixhkbxoic.a2a4j.core.client.AgentCardResolver;
import io.github.pheonixhkbxoic.a2a4j.core.spec.ValueError;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.spec.message.SendTaskStreamingResponse;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import io.github.pheonixhkbxoic.a2a4j.core.util.Uuid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.apache.hc.client5.http.HttpHostConnectException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * @author PheonixHkbxoic
 * @date 2025/5/1 14:48
 * @desc simple cli app
 */
@Slf4j
public class CliApp {
    static HelpFormatter help = HelpFormatter.builder().get();

    public static void main(String[] args) {
        Options options = buildOptions();

        DefaultParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            // 自定义帮助信息
            if (cmd.hasOption("h") || cmd.hasOption("v")) {
                help.printHelp(CliApp.class.getSimpleName(), options, true);
                return;
            }

            if (!cmd.hasOption("u")) {
                help.printHelp("the base url of agent server is absent, you can set with -c", options, false);
                return;
            }

            String command = "send";
            if (cmd.hasOption("c")) {
                command = cmd.getOptionValue("c");
            }
            boolean stream = cmd.hasOption("s");
            int history = 3;
            if (cmd.hasOption("n")) {
                history = Integer.parseInt(cmd.getOptionValue("n"));
            }
            String sessionId = Uuid.uuid4hex();
            if (cmd.hasOption("i")) {
                sessionId = cmd.getOptionValue("i");
            }
            A2AClient client = initClient(cmd.getOptionValue("u"));
            execute(cmd, client, command, stream, history, sessionId);
        } catch (ParseException e) {
            help.printHelp(CliApp.class.getSimpleName(), options, true);
        } catch (CtrlCExitException e) {
            System.out.println(e.getMessage());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof HttpHostConnectException) {
                help.printHelp("the base url connect failed", options, false);
                return;
            }
            help.printHelp(e.getMessage(), options, false);
        }
    }

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "show help");
        options.addOption("v", "verbose", false, "show verbose help");
        options.addOption("u", "url", true, "agent server base url, eg.http://127.0.0.1:8901");
        options.addOption("c", "command", true, "operation command: send(default), get");
        options.addOption("s", "stream", false, "stream response, sse");
        options.addOption("i", "session", false, "chat session id");
        options.addOption("n", "history", false, "history length, default 3");
        return options;
    }

    private static void execute(CommandLine cmd, A2AClient client, String command, boolean stream, int history, String sessionId) {
        switch (command) {
            case "send":
                System.out.println("please input your question! input :exit will exit.");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String question;
                while (true) {
                    try {
                        question = reader.readLine();
                        if (question == null) {
                            throw new CtrlCExitException();
                        }
                        if (question.isBlank()) {
                            continue;
                        }
                        if (":exit".equalsIgnoreCase(question)) {
                            break;
                        }

                        if (stream) {
                            chatStream(client, history, sessionId, question);
                        } else {
                            chat(client, history, sessionId, question);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                }
                break;
            case "get":
                System.out.println("please input taskId! input :exit will exit.");
                BufferedReader reader2 = new BufferedReader(new InputStreamReader(System.in));
                String taskId;
                while (true) {
                    try {
                        taskId = reader2.readLine();
                        if (taskId == null) {
                            throw new CtrlCExitException();
                        }
                        if (taskId.isBlank()) {
                            continue;
                        }
                        if (":exit".equalsIgnoreCase(taskId)) {
                            break;
                        }

                        getTask(client, taskId, history);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                }
                break;
            default:
        }
    }


    private static A2AClient initClient(String agentCardBaseUrl) {
        AgentCard agentCard = new AgentCardResolver(agentCardBaseUrl).resolve();
        return new A2AClient(agentCard);
    }

    private static void chat(A2AClient client, int history, String sessionId, String prompts) {
        TaskSendParams params = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .historyLength(history)
                .acceptedOutputModes(Collections.singletonList("text"))
                .message(new Message(Role.USER, Collections.singletonList(new TextPart(prompts)), null))
                .pushNotification(client.getPushNotificationConfig())
                .build();
        try {
            client.sendTask(params)
                    .flatMap(sendTaskResponse -> {
                        if (sendTaskResponse.getError() != null) {
                            return Mono.error(new ValueError(Util.toJson(sendTaskResponse.getError())));
                        }
                        Task task = sendTaskResponse.getResult();
                        return Mono.just(task.getArtifacts().stream()
                                .flatMap(t -> t.getParts().stream())
                                .filter(p -> Part.TEXT.equals(p.getType()))
                                .map(p -> ((TextPart) p).getText())
                                .filter(t -> !Util.isEmpty(t))
                                .collect(Collectors.joining("\n")));
                    })
                    .doFirst(() -> System.out.printf("[%s:%s] ", sessionId, params.getId()))
                    .subscribe(System.out::println);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static void chatStream(A2AClient client, int history, String sessionId, String prompts) {
        TaskSendParams params = TaskSendParams.builder()
                .id(Uuid.uuid4hex())
                .sessionId(sessionId)
                .historyLength(history)
                .acceptedOutputModes(Collections.singletonList("text"))
                .message(new Message(Role.USER, Collections.singletonList(new TextPart(prompts)), null))
                .pushNotification(client.getPushNotificationConfig())
                .build();
        Flux<SendTaskStreamingResponse> responseFlux = client.sendTaskSubscribe(params);

        responseFlux
                .publishOn(Schedulers.boundedElastic())
                .flatMap(r -> {
                    if (r.getError() != null) {
                        return Flux.error(new ValueError(Util.toJson(r.getError())));
                    }
                    UpdateEvent event = r.getResult();
                    if (event instanceof TaskStatusUpdateEvent) {
                        Message message = ((TaskStatusUpdateEvent) event).getStatus().getMessage();
                        if (message == null) {
                            return Flux.empty();
                        }
                        return Flux.fromStream(message.getParts().stream());
                    }
                    Artifact artifact = ((TaskArtifactUpdateEvent) r.getResult()).getArtifact();
                    return Flux.fromStream(artifact.getParts().stream());
                })
                .filter(p -> Part.TEXT.equals(p.getType()))
                .map(p -> ((TextPart) p).getText())
                .filter(t -> !Util.isEmpty(t))
                .doFirst(() -> System.out.printf("[%s:%s] ", sessionId, params.getId()))
                .doOnComplete(System.out::println)
                .doOnError(System.out::println)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(System.out::print);
    }

    private static void getTask(A2AClient client, String taskId, int history) {
        TaskQueryParams params = new TaskQueryParams(history);
        params.setId(taskId);
        try {
            client.getTask(params)
                    .flatMap(getTaskResponse -> {
                        if (getTaskResponse.getError() != null) {
                            return Mono.error(new ValueError(Util.toJson(getTaskResponse.getError())));
                        }
                        Task task = getTaskResponse.getResult();
                        return Mono.just(task.getHistory().stream()
                                .filter(t -> t.getParts() != null)
                                .flatMap(t -> t.getParts().stream())
                                .filter(p -> Part.TEXT.equals(p.getType()))
                                .map(p -> ((TextPart) p).getText())
                                .filter(t -> !Util.isEmpty(t))
                                .collect(Collectors.joining("\n")));
                    })
                    .subscribe(System.out::println);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }


    static class CtrlCExitException extends RuntimeException {
        public CtrlCExitException() {
            super("ctrl+c exit");
        }
    }
}