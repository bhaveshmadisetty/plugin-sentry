package io.kestra.plugin.sentry;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Renders {@code sentry-template.peb} directly with the variables {@code ExecutionService.executionMap} exposes,
 * so the template can be checked for every execution state without going through a flow trigger.
 */
@KestraTest
class SentryTemplateRenderTest {
    private static final Map<String, Object> EXECUTION = Map.of(
        "id", "exec-1",
        "namespace", "io.kestra.tests",
        "flowId", "monitored-flow",
        "state", Map.of("current", "WARNING")
    );

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void nonFailedExecutionRendersWithoutFirstFailed() throws Exception {
        Map<String, Object> event = render(
            Map.of(
                "execution", EXECUTION,
                "duration", "1s",
                "link", "https://kestra.local/ui/executions/exec-1",
                "firstFailed", false,
                "lastTask", Map.of("taskId", "warned")
            )
        );

        assertThat(exceptionValue(event), is("Execution ended in WARNING after 1s"));
        assertThat(event.get("errors"), nullValue());
    }

    /**
     * A WARNING execution whose only task run is itself WARNING (for example {@code Fail} with {@code allowFailure: true})
     * gets neither {@code firstFailed} nor {@code lastTask} from {@code ExecutionService.executionMap}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void executionWithoutMatchingTaskRunRendersFromTaskRunList() throws Exception {
        Map<String, Object> execution = Map.of(
            "id", "exec-1",
            "namespace", "io.kestra.tests",
            "flowId", "monitored-flow",
            "state", Map.of("current", "WARNING"),
            "taskRunList", List.of(Map.of("taskId", "warned", "state", Map.of("current", "WARNING")))
        );

        Map<String, Object> event = render(
            Map.of(
                "execution", execution,
                "duration", "1s",
                "link", "https://kestra.local/ui/executions/exec-1"
            )
        );

        assertThat(exceptionValue(event), is("Execution ended in WARNING after 1s"));
        assertThat(((Map<String, Object>) event.get("extra")).get("Final task ID"), is("warned"));
    }

    @Test
    void failedExecutionKeepsFailureText() throws Exception {
        Map<String, Object> event = render(
            Map.of(
                "execution", EXECUTION,
                "duration", "1s",
                "link", "https://kestra.local/ui/executions/exec-1",
                "firstFailed", Map.of("taskId", "failed"),
                "lastTask", Map.of("taskId", "failed")
            )
        );

        assertThat(exceptionValue(event), is("Failed on task `failed` after 1s"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorsBlockIsValidJsonOnNonFailedExecution() throws Exception {
        Map<String, Object> event = render(
            Map.of(
                "execution", EXECUTION,
                "duration", "1s",
                "link", "https://kestra.local/ui/executions/exec-1",
                "firstFailed", false,
                "lastTask", Map.of("taskId", "warned"),
                "errors", Map.of("first", "boom", "second", "bang")
            )
        );

        List<Map<String, Object>> errors = (List<Map<String, Object>>) event.get("errors");
        assertThat(errors.size(), is(2));
        assertThat(errors.getFirst().get("type"), is("unknown_error"));
        assertThat(errors.stream().filter(e -> e.get("path").equals("first")).findFirst().orElseThrow().get("details"), is("boom"));
        assertThat(exceptionValue(event), is("Execution ended in WARNING after 1s"));
    }

    private Map<String, Object> render(Map<String, Object> variables) throws Exception {
        String template = IOUtils.toString(
            Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("sentry-template.peb")),
            StandardCharsets.UTF_8
        );
        RunContext runContext = runContextFactory.of();
        String rendered = runContext.render(template, variables);

        return JacksonMapper.ofJson().readValue(rendered, new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static String exceptionValue(Map<String, Object> event) {
        Map<String, Object> exception = (Map<String, Object>) event.get("exception");
        List<Map<String, Object>> values = (List<Map<String, Object>>) exception.get("values");

        return (String) values.getFirst().get("value");
    }
}
