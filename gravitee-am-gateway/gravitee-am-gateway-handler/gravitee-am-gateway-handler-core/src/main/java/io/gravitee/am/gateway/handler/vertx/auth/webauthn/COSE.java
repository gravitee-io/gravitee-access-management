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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import io.gravitee.am.gateway.handler.vertx.auth.jose.JWK;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class COSE {

    private static class NV {
        private String name;
        private Map<String, String> values;

        NV(String name) {
            this.name = name;
            this.values = new HashMap<>();
        }

        NV add(String key, String value) {
            values.put(key, value);
            return this;
        }
    }

    // main COSE labels
    // defined here: https://tools.ietf.org/html/rfc8152#section-7.1
    private static final Map<String, NV> COSE_LABELS = new HashMap<String, NV>() {{
        put("1",
                new NV("kty")
                        .add("2", "EC")
                        .add("3", "RSA"));
        put("2",
                new NV("kid"));
        put("3",
                new NV("alg")
                        .add("-7", "ES256")
                        .add("-8", "EdDSA")
                        .add("-35", "ES384")
                        .add("-36", "ES512")
                        .add("-257", "RS256")
                        .add("-258", "RS384")
                        .add("-259", "RS512")
                        .add("-65535", "RS1"));
        put("4",
                new NV("key_ops"));
        put("5",
                new NV("base_iv"));
    }};

    // ECDSA key parameters
    // defined here: https://tools.ietf.org/html/rfc8152#section-13.1.1
    private static final Map<String, NV> EC_KEY_PARAMS = new HashMap<String, NV>() {{
        put("-1",
                new NV("crv")
                        .add("1", "P-256")
                        .add("2", "P-384")
                        .add("3", "P-521")
                        .add("4", "X25519")
                        .add("5", "X448")
                        .add("6", "Ed25519")
                        .add("7", "Ed448")
        );
        put("-2",
                new NV("x"));
        put("-3",
                new NV("y"));
        put("-4",
                new NV("d"));
    }};

    // RSA key parameters
    // defined here: https://tools.ietf.org/html/rfc8230#section-4
    private static final Map<String, NV> RSA_KEY_PARAMS = new HashMap<String, NV>() {{
        put("-1",
                new NV("n"));
        put("-2",
                new NV("e"));
        put("-3",
                new NV("d"));
        put("-4",
                new NV("p"));
        put("-5",
                new NV("q"));
        put("-6",
                new NV("dp"));
        put("-7",
                new NV("dq"));
        put("-8",
                new NV("qi"));
        put("-9",
                new NV("other"));
        put("-10",
                new NV("r_i"));
        put("-11",
                new NV("d_i"));
        put("-12",
                new NV("t_i"));
    }};

    private static final Map<String, Map<String, NV>> KEY_PARAMS = new HashMap<String, Map<String, NV>>() {{
        put("EC", EC_KEY_PARAMS);
        put("RSA", RSA_KEY_PARAMS);
    }};

    public static JWK toJWK(Map<String, Object> coseMap) {
        JsonObject retKey = new JsonObject();
        Map<String, String> extraMap = new HashMap<>();

        // parse main COSE labels
        for (Map.Entry<String, Object> kv : coseMap.entrySet()) {
            String key = kv.getKey();
            String value = kv.getValue().toString();

            if (!COSE_LABELS.containsKey(key)) {
                extraMap.put(key, value);
                continue;
            }

            String name = COSE_LABELS.get(key).name;
            if (COSE_LABELS.get(key).values.containsKey(value)) {
                value = COSE_LABELS.get(key).values.get(value);
            }

            retKey.put(name, value);
        }

        Map<String, NV> keyParams = KEY_PARAMS.get(retKey.getString("kty"));

        // parse key-specific parameters
        for (Map.Entry<String, String> kv : extraMap.entrySet()) {
            String key = kv.getKey();
            String value = kv.getValue();

            if (!keyParams.containsKey(key)) {
                throw new RuntimeException("unknown COSE key label: " + retKey.getString("kty") + " " + key);
            }

            String name = keyParams.get(key).name;

            if (keyParams.get(key).values.containsKey(value)) {
                value = keyParams.get(key).values.get(value);
            }

            retKey.put(name, value);
        }

        if ("EC".equals(retKey.getString("kty"))) {
            // JWK will assume ASN.1 signature encoding for EC which isn't valid for COSE
            retKey.put("asn1", false);
        }

        return new JWK(retKey);
    }
}
