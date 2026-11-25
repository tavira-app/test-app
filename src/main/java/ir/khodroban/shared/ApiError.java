package ir.khodroban.shared;

import java.util.List;

public record ApiError(String type, String title, int status, String detail,
                       String instance, String code, String traceId,
                       List<FieldViolation> violations) {
    public record FieldViolation(String field, String message) {}
}
