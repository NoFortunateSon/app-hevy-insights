package io.github.nofortunateson.hevyinsights.data.hevy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HevyApiProperties.class)
class HevyClientConfiguration {

    /** Header name documented by Hevy for the user's API key. */
    static final String API_KEY_HEADER = "api-key";

    @Bean
    HevyClient hevyClient(HevyApiProperties properties) {
        return new HevyClient(restClientBuilder(properties).build(), Sleeper.REAL,
                properties.maxRateLimitAttempts());
    }

    /**
     * Base URL plus the API key header. The key is checked when a request is sent, not at
     * startup, so the app can start without it (for example to serve stored metrics).
     */
    static RestClient.Builder restClientBuilder(HevyApiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestInterceptor((request, body, execution) -> {
                    if (!properties.hasKey()) {
                        throw new IllegalStateException("Hevy API key missing: set HEVY_API_KEY");
                    }
                    request.getHeaders().set(API_KEY_HEADER, properties.key());
                    return execution.execute(request, body);
                });
    }
}
