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
 * Converter and mapper for {@link Authenticator}.
 * NOTE: This class has been automatically generated from the {@link Authenticator} original class using Vert.x codegen.
 */
// TODO to remove when updating to vert.x 4
public class AuthenticatorConverter {

    public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Authenticator obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
                case "counter":
                    if (member.getValue() instanceof Number) {
                        obj.setCounter(((Number)member.getValue()).longValue());
                    }
                    break;
                case "credID":
                    if (member.getValue() instanceof String) {
                        obj.setCredID((String)member.getValue());
                    }
                    break;
                case "publicKey":
                    if (member.getValue() instanceof String) {
                        obj.setPublicKey((String)member.getValue());
                    }
                    break;
                case "type":
                    if (member.getValue() instanceof String) {
                        obj.setType((String)member.getValue());
                    }
                    break;
                case "userName":
                    if (member.getValue() instanceof String) {
                        obj.setUserName((String)member.getValue());
                    }
                    break;
                case "aaguid":
                    if (member.getValue() instanceof String) {
                        obj.setAaguid((String)member.getValue());
                    }
                    break;
                case "fmt":
                    if (member.getValue() instanceof String) {
                        obj.setAttestationStatementFormat((String)member.getValue());
                    }
                    break;
                case "attStmt":
                    if (member.getValue() instanceof String) {
                        obj.setAttestationStatement((String)member.getValue());
                    }
                    break;
            }
        }
    }

    public static void toJson(Authenticator obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    public static void toJson(Authenticator obj, java.util.Map<String, Object> json) {
        json.put("counter", obj.getCounter());
        if (obj.getCredID() != null) {
            json.put("credID", obj.getCredID());
        }
        if (obj.getPublicKey() != null) {
            json.put("publicKey", obj.getPublicKey());
        }
        if (obj.getType() != null) {
            json.put("type", obj.getType());
        }
        if (obj.getUserName() != null) {
            json.put("userName", obj.getUserName());
        }
        if (obj.getAaguid() != null) {
            json.put("aaguid", obj.getAaguid());
        }
        if (obj.getAttestationStatementFormat() != null) {
            json.put("fmt", obj.getAttestationStatementFormat());
        }
        if (obj.getAttestationStatement() != null) {
            json.put("attStmt", obj.getAttestationStatement());
        }
    }
}
