package com.colfondos.contactupdate.client.sim;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de la simulación (perfil {@code sim}). El OTP fijo solo existe aquí:
 * los adaptadores simulados no se cargan fuera de ese perfil.
 */
@ConfigurationProperties(prefix = "contact-update.simulation")
public record SimulationProperties(String fixedOtp, Duration slowCallDelay) {
}
