package io.github.pheonixhkbxoic.a2a4j.examples.agents.echoagent.controller;

import io.github.pheonixhkbxoic.a2a4j.core.server.A2AServer;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 23:33
 * @desc
 */
@Controller
public class IndexController {
    @Resource
    A2AServer a2aServer;

    @ResponseBody
    @GetMapping("/echo")
    public String index() {
        return "echo app";
    }

    @ResponseBody
    @GetMapping("/server")
    public ResponseEntity<String> server() {
        return ResponseEntity.ok(a2aServer.getState());
    }

    @ResponseBody
    @GetMapping("/server/start")
    public ResponseEntity<String> startServer() {
        return ResponseEntity.ok(a2aServer.start().getState());
    }

    @ResponseBody
    @GetMapping("/server/close")
    public ResponseEntity<String> closeServer() {
        return ResponseEntity.ok(a2aServer.close().getState());
    }


}
