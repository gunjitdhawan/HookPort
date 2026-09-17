package io.hookport.endpoint;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class EndpointEtag {

    private EndpointEtag() {
    }

    public static String fromVersion(long version) {
        return "\"" + version + "\"";
    }

    public static long parseRequired(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "The If-Match header is required"
            );
        }

        String value = ifMatch.trim();

        if (value.startsWith("W/")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Weak ETags cannot be used for updates"
            );
        }

        if (value.contains(",")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only one ETag is currently supported"
            );
        }

        if (!value.matches("\"\\d+\"")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "If-Match must contain a valid quoted version"
            );
        }

        String numericVersion = value.substring(
                1,
                value.length() - 1
        );

        try {
            return Long.parseLong(numericVersion);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "If-Match contains an invalid version"
            );
        }
    }
}