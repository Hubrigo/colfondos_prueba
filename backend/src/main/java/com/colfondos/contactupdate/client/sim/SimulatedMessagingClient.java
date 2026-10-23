package com.colfondos.contactupdate.client.sim;

import com.colfondos.contactupdate.client.MessagingClient;
import com.colfondos.contactupdate.client.Provider;
import com.colfondos.contactupdate.client.ProviderException;
import com.colfondos.contactupdate.observability.Masking;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Simula Messaging API. Para el afiliado del escenario CA-14 falla siempre con 503. */
@Profile("sim")
@Component
public class SimulatedMessagingClient implements MessagingClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedMessagingClient.class);

    @Override
    public MessageReceipt sendEmail(EmailNotification notification) {
        if (SimulationScenarios.FAILING_MESSAGING.contains(notification.customerId())) {
            throw new ProviderException(Provider.MESSAGING, ProviderException.Kind.UNAVAILABLE, "HTTP 503 simulado");
        }
        log.atInfo().addKeyValue("template", notification.template())
                .addKeyValue("to", Masking.email(notification.to()))
                .log("Correo simulado enviado");
        return new MessageReceipt(UUID.randomUUID().toString());
    }
}
