package io.hookport.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

public record ValidatedTarget(
        URI uri,
        List<InetAddress> resolvedAddresses
) {
}