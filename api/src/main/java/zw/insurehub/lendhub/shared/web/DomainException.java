package zw.insurehub.lendhub.shared.web;

import org.springframework.http.HttpStatus;

/** Base for business errors; mapped to RFC 7807 ProblemDetails by {@link ApiExceptionHandler}. */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** A business rule was broken (422). */
    public static DomainException rule(String code, String message) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static DomainException notFound(String what, Object id) {
        return new DomainException(HttpStatus.NOT_FOUND, "not-found", what + " " + id + " was not found");
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    /** Authenticated but not allowed by a domain rule such as maker-checker or an approval limit (403). */
    public static DomainException forbidden(String code, String message) {
        return new DomainException(HttpStatus.FORBIDDEN, code, message);
    }
}
