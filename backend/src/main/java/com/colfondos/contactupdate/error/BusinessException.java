package com.colfondos.contactupdate.error;

import java.util.Map;

/** Error de negocio esperado: se traduce a ProblemDetail sin stack trace. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> extra;

    public BusinessException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public BusinessException(ErrorCode code, String detail, Map<String, Object> extra) {
        super(detail);
        this.code = code;
        this.extra = Map.copyOf(extra);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> extra() {
        return extra;
    }
}
