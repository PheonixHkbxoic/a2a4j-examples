package io.github.pheonixhkbxoic.a2a4j.examples.agents.echoagent.controller;

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

    @ResponseBody
    @GetMapping("/echo")
    public String index() {
        return "echo app";
    }
}
