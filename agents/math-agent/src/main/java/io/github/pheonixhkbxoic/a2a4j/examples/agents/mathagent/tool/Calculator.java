package io.github.pheonixhkbxoic.a2a4j.examples.agents.mathagent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/27 00:54
 * @desc calculator tools
 */
public class Calculator {

    @Tool("Returns a square root of a given number, you can set precision")
    public String squareRoot(@P("given number") double x, @P(value = "精度", required = false) int precision) {
        return String.format("%." + precision + "f", Math.sqrt(x));
    }

    @Tool("Returns sum of two integers")
    public int add(int a, int b) {
        return a + b;
    }

}
