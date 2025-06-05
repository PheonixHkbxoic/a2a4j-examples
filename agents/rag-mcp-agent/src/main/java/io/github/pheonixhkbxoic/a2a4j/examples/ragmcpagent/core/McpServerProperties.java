package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.client.McpServerParameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author PheonixHkbxoic
 * @date 2025/6/2 22:46
 * @desc
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class McpServerProperties {
    private String command;
    private List<String> args = new ArrayList<>();
    private Map<String, String> env = new HashMap<>();

    private String type = McpChannel.STDIO;
    private String url;
    private boolean disabled;
    private String note;


    McpServerParameters toMcpServerParameters(Map<String, String> commandPathMap) {
        McpServerParameters ps = new McpServerParameters();
        ps.setCommand(command);
        if (commandPathMap != null && !commandPathMap.isEmpty()) {
            String realCommand = commandPathMap.getOrDefault(command, command);
            ps.setCommand(realCommand);
        }
        ps.setArgs(args);
        ps.setEnv(env);
        return ps;
    }
}
