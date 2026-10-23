package com.colfondos.contactupdate.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final ContactUpdateProperties properties;

    public AppConfig(ContactUpdateProperties properties) {
        this.properties = properties;
    }

    /** Reloj inyectable: las pruebas controlan el tiempo (vigencia del OTP, INC-003). */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(properties.zoneId()));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.corsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT")
                .allowedHeaders("Content-Type", "X-Correlation-Id", "Idempotency-Key")
                .exposedHeaders("X-Correlation-Id");
    }
}
