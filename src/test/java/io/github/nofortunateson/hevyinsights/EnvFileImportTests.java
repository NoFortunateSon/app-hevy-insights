package io.github.nofortunateson.hevyinsights;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The {@code spring.config.import} of {@code application.yaml} reads a local {@code .env} file (issue #36).
 * Runs a bare Spring application (no database, no web) that only loads the configuration. Data is invented.
 */
class EnvFileImportTests {

    private static final String MARKER = "HEVY_INSIGHTS_TEST_MARKER";

    @TempDir
    private Path dir;

    @Test
    void propertiesOfTheEnvFileAreLoadedAndUsableAsPlaceholders() throws IOException {
        Path envFile = Files.writeString(dir.resolve(".env"), """
                # comment lines are allowed
                %s=from-env-file
                HEVY_API_KEY=not-a-real-key
                """.formatted(MARKER));

        Environment environment = environmentWithEnvFile(envFile);

        assertThat(environment.getProperty(MARKER)).isEqualTo("from-env-file");
        assertThat(environment.getProperty("hevy.api.key")).isEqualTo("not-a-real-key");
    }

    @Test
    void missingEnvFileIsNotAnError() {
        Environment environment = environmentWithEnvFile(dir.resolve("absent.env"));

        assertThat(environment.getProperty(MARKER)).isNull();
    }

    @Test
    void testRunPointsTheEnvFileToAMissingPath() {
        String envFile = System.getProperty("HEVY_INSIGHTS_ENV_FILE");

        assertThat(envFile).as("set by Surefire in pom.xml").isNotBlank();
        assertThat(Path.of(envFile)).doesNotExist();
    }

    private static Environment environmentWithEnvFile(Path envFile) {
        String location = envFile.toAbsolutePath().toString().replace('\\', '/');
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BareConfiguration.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run("--HEVY_INSIGHTS_ENV_FILE=" + location)) {
            return context.getEnvironment();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BareConfiguration {
    }
}
