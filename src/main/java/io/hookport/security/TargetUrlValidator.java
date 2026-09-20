package io.hookport.security;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class TargetUrlValidator {

    private static final Set<String> ALLOWED_SCHEMES =
            Set.of("http", "https");

    private final WebhookSecurityProperties properties;

    public TargetUrlValidator(
            WebhookSecurityProperties properties
    ) {
        this.properties = properties;
    }

    public ValidatedTarget validate(String targetUrl) {
        final URI uri;

        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException exception) {
            throw new UnsafeTargetUrlException(
                    "Target URL is invalid"
            );
        }

        validateStructure(uri);

        List<InetAddress> addresses = resolve(uri.getHost());

        if (!properties.isAllowPrivateTargets()) {
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw new UnsafeTargetUrlException(
                            "Target resolves to a private or reserved address"
                    );
                }
            }
        }

        return new ValidatedTarget(uri, addresses);
    }

    private void validateStructure(URI uri) {
        String scheme = uri.getScheme();

        if (scheme == null ||
                !ALLOWED_SCHEMES.contains(
                        scheme.toLowerCase(Locale.ROOT)
                )) {
            throw new UnsafeTargetUrlException(
                    "Only HTTP and HTTPS targets are allowed"
            );
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new UnsafeTargetUrlException(
                    "Target URL must contain a hostname"
            );
        }

        if (uri.getUserInfo() != null) {
            throw new UnsafeTargetUrlException(
                    "Target URL must not contain credentials"
            );
        }

        if (uri.getFragment() != null) {
            throw new UnsafeTargetUrlException(
                    "Target URL must not contain a fragment"
            );
        }
    }

    private List<InetAddress> resolve(String host) {
        try {
            return List.of(InetAddress.getAllByName(host));
        } catch (UnknownHostException exception) {
            throw new UnsafeTargetUrlException(
                    "Target hostname could not be resolved"
            );
        }
    }

    private boolean isPublic(InetAddress address) {
        return !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isSiteLocalAddress()
                && !address.isMulticastAddress()
                && !isCarrierGradeNat(address);
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();

        if (bytes.length != 4) {
            return false;
        }

        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);

        return first == 100 && second >= 64 && second <= 127;
    }
}