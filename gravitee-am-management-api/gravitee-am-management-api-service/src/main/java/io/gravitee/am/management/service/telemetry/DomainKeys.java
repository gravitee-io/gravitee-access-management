/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.management.service.telemetry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns a raw domain identifier into the pseudonymous key the reports carry.
 * <p>
 * The key is an HMAC-SHA256 of the domain identifier under the installation identifier, truncated
 * to 16 hexadecimal characters. It is stable across passes, so the collector can follow one domain
 * over time, and it is meaningless outside the installation that produced it.
 *
 * @author GraviteeSource Team
 */
public final class DomainKeys {

    private static final int KEY_LENGTH = 16;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private DomainKeys() {}

    public static String key(String installationId, String domainId) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(installationId.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return truncatedHex(mac.doFinal(domainId.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive the telemetry domain key", e);
        }
    }

    /**
     * Hashes the stable part of a domain record. The collector compares it against the row it
     * already holds, so an unchanged domain costs no write.
     */
    public static String fingerprint(String content) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return truncatedHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to derive the telemetry domain fingerprint", e);
        }
    }

    private static String truncatedHex(byte[] bytes) {
        final StringBuilder hex = new StringBuilder();
        for (int i = 0; hex.length() < KEY_LENGTH; i++) {
            hex.append(String.format("%02x", bytes[i]));
        }
        return hex.substring(0, KEY_LENGTH);
    }
}
