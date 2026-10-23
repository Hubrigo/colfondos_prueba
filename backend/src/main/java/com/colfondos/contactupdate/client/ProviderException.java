package com.colfondos.contactupdate.client;

/**
 * Falla técnica de un proveedor externo. Solo las fallas transitorias
 * (timeout, 5xx, error de red) se reintentan; nunca los resultados de negocio.
 */
public class ProviderException extends RuntimeException {

    public enum Kind {
        /** Sin respuesta dentro del timeout (INC-002). */
        TIMEOUT,
        /** 5xx, error de red o circuito abierto (INC-005). */
        UNAVAILABLE
    }

    private final Provider provider;
    private final Kind kind;

    public ProviderException(Provider provider, Kind kind, String message) {
        super(message);
        this.provider = provider;
        this.kind = kind;
    }

    public Provider provider() {
        return provider;
    }

    public Kind kind() {
        return kind;
    }
}
