package com.colfondos.contactupdate.client.sim;

import com.colfondos.contactupdate.client.CustomerDataClient.Customer;
import java.util.List;
import java.util.Set;

/** Datos sintéticos por escenario (E2 §9). Ninguno corresponde a una persona real. */
final class SimulationScenarios {

    record SyntheticCustomer(String documentType, String documentNumber, Customer customer) {
    }

    /** CC 1012345678: camino feliz (CA-01). */
    static final String HAPPY = "AF-1001";
    /** CC 1000000001: Customer Data con timeout en el primer intento de actualización (CA-12). */
    static final String SLOW_UPDATE = "AF-1002";
    /** CC 1000000002: Identity Capability caído (CA-13). */
    static final String IDENTITY_DOWN = "AF-1003";
    /** CC 1000000003: Messaging falla siempre → notificación PENDING (CA-14). */
    static final String MESSAGING_DOWN = "AF-1004";
    /** CC 1000000004: correo actual conocido, para probar "igual al actual" (CA-08). */
    static final String SAME_EMAIL = "AF-1005";

    static final Set<String> FAILING_MESSAGING = Set.of(MESSAGING_DOWN);

    static List<SyntheticCustomer> customers() {
        return List.of(
                synthetic("1012345678", HAPPY, "ana.perez@correo-sim.test", "3001234567"),
                synthetic("1000000001", SLOW_UPDATE, "luis.gomez@correo-sim.test", "3109876543"),
                synthetic("1000000002", IDENTITY_DOWN, "marta.diaz@correo-sim.test", "3205554433"),
                synthetic("1000000003", MESSAGING_DOWN, "jorge.rios@correo-sim.test", "3157778899"),
                synthetic("1000000004", SAME_EMAIL, "actual@correo.com", "3012223344"));
    }

    private static SyntheticCustomer synthetic(String number, String id, String email, String phone) {
        return new SyntheticCustomer("CC", number, new Customer(id, email, phone, "ACTIVE"));
    }

    private SimulationScenarios() {
    }
}
