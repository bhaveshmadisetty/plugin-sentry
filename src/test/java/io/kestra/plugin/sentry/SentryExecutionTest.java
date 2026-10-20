package io.kestra.plugin.sentry;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@KestraTest
public class SentryExecutionTest extends AbstractSentryTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(SentryExecutionTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var failedExecution = runAndCaptureExecution(
            "main-flow-that-fails",
            "sentry"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString(failedExecution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString("Failed on task `failed`"));
        assertThat(receivedData, containsString("\"Final task ID\":\"failed\""));
        assertThat(receivedData, containsString("Kestra Sentry alert"));
        assertThat(receivedData, containsString("\"customMessage\":\"Custom failure message for Sentry\""));
        assertThat(receivedData, containsString("\"environment\":\"production\""));
        assertThat(receivedData, containsString("\"team\":\"sre-core\""));
    }

    @Test
    void warningExecution() throws Exception {
        var warningExecution = runAndCaptureExecution(
            "main-flow-that-warns",
            "sentry-warning"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString(warningExecution.getId()));
        assertThat(receivedData, containsString("Execution ended in WARNING after"));
        assertThat(receivedData, not(containsString("Failed on task")));
        assertThat(receivedData, containsString("\"Execution Status\":\"WARNING\""));
        assertThat(receivedData, containsString("\"Final task ID\":\"warned\""));
        assertThat(receivedData, containsString("\"level\":\"warning\""));
        assertThat(receivedData, containsString("\"customMessage\":\"Custom warning message for Sentry\""));
    }

    @Test
    void successExecution() throws Exception {
        var successExecution = runAndCaptureExecution(
            "main-flow-that-succeeds",
            "sentry-success"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString(successExecution.getId()));
        assertThat(receivedData, containsString("Execution ended in SUCCESS after"));
        assertThat(receivedData, not(containsString("Failed on task")));
        assertThat(receivedData, containsString("\"Execution Status\":\"SUCCESS\""));
        assertThat(receivedData, containsString("\"Final task ID\":\"success\""));
        assertThat(receivedData, containsString("\"customMessage\":\"Custom success message for Sentry\""));
    }

}
