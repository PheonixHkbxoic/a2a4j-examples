package io.github.pheonixhkbxoic.a2a4j.examples.hosts.multiagent.schema;

import lombok.Data;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/26 17:04
 * @desc execute context
 */

@Data
public class ExecuteContext {
    public static final String DEFAULT_AGENT_NAME = "None";

    /**
     * user id
     */
    protected String userId;

    /**
     * session id
     */
    protected String sessionId;

    /**
     * available state: over means find a suitable agent; input_required means extra message or data is required
     */
    protected String state;

    protected ExecuteContextResult result;

    @Data
    public static class ExecuteContextResult {
        /**
         * the active agent name
         */
        private String activeAgent;
        /**
         * the answer of user's question if not found suitable agent
         */
        private String answer;
    }

    public static class ExecuteContextState {
        public static String OVER = "over";
        public static String INPUT_REQUIRED = "input_required";
    }
}
