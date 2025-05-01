package io.github.pheonixhkbxoic.a2a4j.examples.hosts.cli.test;

import io.github.pheonixhkbxoic.a2a4j.examples.hosts.cli.CliApp;
import org.junit.jupiter.api.Test;

/**
 * @author PheonixHkbxoic
 * @date 2025/5/1 16:22
 * @desc
 */
public class CliAppTests {

    @Test
    public void testHelp() {
        CliApp.main(new String[]{});
        CliApp.main(new String[]{"-h"});
        CliApp.main(new String[]{"-v"});
        CliApp.main(new String[]{"-h", "-v"});
    }

    @Test
    public void testSend() {
        CliApp.main(new String[]{"-u", "http://127.0.0.1:8901", "hello"});
    }

}
