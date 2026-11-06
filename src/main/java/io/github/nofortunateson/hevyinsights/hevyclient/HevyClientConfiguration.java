package io.github.nofortunateson.hevyinsights.hevyclient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HevyApiProperties.class)
class HevyClientConfiguration {

    @Bean
    HevyClient hevyClient(HevyApiProperties properties) {
        return new HevyClient(restClientBuilder(properties).build(), Sleeper.REAL,
                properties.maxRateLimitAttempts());
    }

    /**
     * Base URL plus the {@code api-key} header. The key is checked when a request is sent, not at
     * startup, so the app can start without it (for example to serve stored metrics).
     */
    static RestClient.Builder restClientBuilder(HevyApiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestInterceptor((request, body, execution) -> {
                    if (!properties.hasKey()) {
                        throw new IllegalStateException("Hevy API key missing: set HEVY_API_KEY");
                    }
                    request.getHeaders().set("api-key", properties.key());
                    return execution.execute(request, body);
                });
    }
}
