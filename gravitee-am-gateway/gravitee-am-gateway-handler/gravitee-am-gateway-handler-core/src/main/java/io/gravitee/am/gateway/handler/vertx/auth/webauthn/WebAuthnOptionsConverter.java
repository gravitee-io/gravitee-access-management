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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.*;

/**
 * Converter and mapper for {@link io.vertx.ext.auth.webauthn.WebAuthnOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.ext.auth.webauthn.WebAuthnOptions} original class using Vert.x codegen.
 */
// TODO to remove when updating to vert.x 4
public class WebAuthnOptionsConverter {


    public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, WebAuthnOptions obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
                case "attestation":
                    if (member.getValue() instanceof String) {
                        obj.setAttestation(Attestation.valueOf((String)member.getValue()));
                    }
                    break;
                case "authenticatorAttachment":
                    if (member.getValue() instanceof String) {
                        obj.setAuthenticatorAttachment(AuthenticatorAttachment.valueOf((String)member.getValue()));
                    }
                    break;
                case "challengeLength":
                    if (member.getValue() instanceof Number) {
                        obj.setChallengeLength(((Number)member.getValue()).intValue());
                    }
                    break;
                case "extensions":
                    if (member.getValue() instanceof JsonObject) {
                        obj.setExtensions(((JsonObject)member.getValue()).copy());
                    }
                    break;
                case "pubKeyCredParams":
                    if (member.getValue() instanceof JsonArray) {
                        java.util.ArrayList<PublicKeyCredential> list =  new java.util.ArrayList<>();
                        ((Iterable<Object>)member.getValue()).forEach( item -> {
                            if (item instanceof String)
                                list.add(PublicKeyCredential.valueOf((String)item));
                        });
                        obj.setPubKeyCredParams(list);
                    }
                    break;
                case "relyingParty":
                    if (member.getValue() instanceof JsonObject) {
                        obj.setRelyingParty(new RelyingParty((JsonObject)member.getValue()));
                    }
                    break;
                case "requireResidentKey":
                    if (member.getValue() instanceof Boolean) {
                        obj.setRequireResidentKey((Boolean)member.getValue());
                    }
                    break;
                case "rootCertificates":
                    if (member.getValue() instanceof JsonObject) {
                        java.util.Map<String, java.lang.String> map = new java.util.LinkedHashMap<>();
                        ((Iterable<java.util.Map.Entry<String, Object>>)member.getValue()).forEach(entry -> {
                            if (entry.getValue() instanceof String)
                                map.put(entry.getKey(), (String)entry.getValue());
                        });
                        obj.setRootCertificates(map);
                    }
                    break;
                case "rootCrls":
                    if (member.getValue() instanceof JsonArray) {
                        java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
                        ((Iterable<Object>)member.getValue()).forEach( item -> {
                            if (item instanceof String)
                                list.add((String)item);
                        });
                        obj.setRootCrls(list);
                    }
                    break;
                case "timeout":
                    if (member.getValue() instanceof Number) {
                        obj.setTimeout(((Number)member.getValue()).longValue());
                    }
                    break;
                case "transports":
                    if (member.getValue() instanceof JsonArray) {
                        java.util.ArrayList<AuthenticatorTransport> list =  new java.util.ArrayList<>();
                        ((Iterable<Object>)member.getValue()).forEach( item -> {
                            if (item instanceof String)
                                list.add(AuthenticatorTransport.valueOf((String)item));
                        });
                        obj.setTransports(list);
                    }
                    break;
                case "userVerification":
                    if (member.getValue() instanceof String) {
                        obj.setUserVerification(UserVerification.valueOf((String)member.getValue()));
                    }
                    break;
            }
        }
    }

    public static void toJson(WebAuthnOptions obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    public static void toJson(WebAuthnOptions obj, java.util.Map<String, Object> json) {
        if (obj.getAttestation() != null) {
            json.put("attestation", obj.getAttestation().name());
        }
        if (obj.getAuthenticatorAttachment() != null) {
            json.put("authenticatorAttachment", obj.getAuthenticatorAttachment().name());
        }
        json.put("challengeLength", obj.getChallengeLength());
        if (obj.getExtensions() != null) {
            json.put("extensions", obj.getExtensions());
        }
        if (obj.getPubKeyCredParams() != null) {
            JsonArray array = new JsonArray();
            obj.getPubKeyCredParams().forEach(item -> array.add(item.name()));
            json.put("pubKeyCredParams", array);
        }
        if (obj.getRelyingParty() != null) {
            json.put("relyingParty", obj.getRelyingParty().toJson());
        }
        json.put("requireResidentKey", obj.getRequireResidentKey());
        if (obj.getTimeout() != null) {
            json.put("timeout", obj.getTimeout());
        }
        if (obj.getTransports() != null) {
            JsonArray array = new JsonArray();
            obj.getTransports().forEach(item -> array.add(item.name()));
            json.put("transports", array);
        }
        if (obj.getUserVerification() != null) {
            json.put("userVerification", obj.getUserVerification().name());
        }
    }
}
