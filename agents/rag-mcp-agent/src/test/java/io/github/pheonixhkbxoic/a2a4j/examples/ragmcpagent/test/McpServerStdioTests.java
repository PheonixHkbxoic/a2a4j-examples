package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author PheonixHkbxoic
 * @date 2025/6/4 23:31
 * @desc
 */
public class McpServerStdioTests {

    @Test
    public void exec() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("C:/Program Files/nodejs/npx.cmd",
                "-y",
                "@modelcontextprotocol/server-filesystem",
                "D:/");
        pb.start();
    }

}
