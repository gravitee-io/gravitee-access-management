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

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.vertx.ext.auth.webauthn.WebAuthnCredentials}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.ext.auth.webauthn.WebAuthnCredentials} original class using Vert.x codegen.
 */
// TODO to remove when updating to vert.x 4
public class WebAuthnCredentialsConverter {


    public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, WebAuthnCredentials obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
                case "challenge":
                    if (member.getValue() instanceof String) {
                        obj.setChallenge((String)member.getValue());
                    }
                    break;
                case "domain":
                    if (member.getValue() instanceof String) {
                        obj.setDomain((String)member.getValue());
                    }
                    break;
                case "origin":
                    if (member.getValue() instanceof String) {
                        obj.setOrigin((String)member.getValue());
                    }
                    break;
                case "username":
                    if (member.getValue() instanceof String) {
                        obj.setUsername((String)member.getValue());
                    }
                    break;
                case "webauthn":
                    if (member.getValue() instanceof JsonObject) {
                        obj.setWebauthn(((JsonObject)member.getValue()).copy());
                    }
                    break;
            }
        }
    }

    public static void toJson(WebAuthnCredentials obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    public static void toJson(WebAuthnCredentials obj, java.util.Map<String, Object> json) {
        if (obj.getChallenge() != null) {
            json.put("challenge", obj.getChallenge());
        }
        if (obj.getDomain() != null) {
            json.put("domain", obj.getDomain());
        }
        if (obj.getOrigin() != null) {
            json.put("origin", obj.getOrigin());
        }
        if (obj.getUsername() != null) {
            json.put("username", obj.getUsername());
        }
        if (obj.getWebauthn() != null) {
            json.put("webauthn", obj.getWebauthn());
        }
    }
}
