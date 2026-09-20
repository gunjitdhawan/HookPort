package io.hookport.delivery;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    public String sign(
            String encodedSecret,
            long timestamp,
            byte[] body
    ) {
        try {
            byte[] secret = Base64.getUrlDecoder()
                    .decode(encodedSecret);

            Mac mac = Mac.getInstance(ALGORITHM);

            mac.init(new SecretKeySpec(
                    secret,
                    ALGORITHM
            ));

            mac.update(
                    Long.toString(timestamp)
                            .getBytes(StandardCharsets.UTF_8)
            );

            mac.update((byte) '.');

            byte[] signature = mac.doFinal(body);

            return "v1=" + HexFormat.of()
                    .formatHex(signature);

        } catch (
                GeneralSecurityException |
                IllegalArgumentException exception
        ) {
            throw new IllegalStateException(
                    "Unable to sign webhook payload",
                    exception
            );
        }
    }
}