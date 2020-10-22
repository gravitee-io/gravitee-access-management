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
package io.gravitee.am.gateway.handler.vertx.auth;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link PubSecKeyOptions}.
 * NOTE: This class has been automatically generated from the {@link PubSecKeyOptions} original class using Vert.x codegen.
 */
// TODO to remove when updating to vert.x 4
public class PubSecKeyOptionsConverter {


    public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PubSecKeyOptions obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
                case "algorithm":
                    if (member.getValue() instanceof String) {
                        obj.setAlgorithm((String) member.getValue());
                    }
                    break;
                case "buffer":
                    if (member.getValue() instanceof String) {
                        obj.setBuffer((String) member.getValue());
                    }
                    break;
                case "certificate":
                    if (member.getValue() instanceof Boolean) {
                        obj.setCertificate((Boolean) member.getValue());
                    }
                    break;
                case "id":
                    if (member.getValue() instanceof String) {
                        obj.setId((String) member.getValue());
                    }
                    break;
                case "publicKey":
                    if (member.getValue() instanceof String) {
                        obj.setPublicKey((String) member.getValue());
                    }
                    break;
                case "secretKey":
                    if (member.getValue() instanceof String) {
                        obj.setSecretKey((String) member.getValue());
                    }
                    break;
                case "symmetric":
                    if (member.getValue() instanceof Boolean) {
                        obj.setSymmetric((Boolean) member.getValue());
                    }
                    break;
            }
        }
    }

    public static void toJson(PubSecKeyOptions obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    public static void toJson(PubSecKeyOptions obj, java.util.Map<String, Object> json) {
        if (obj.getAlgorithm() != null) {
            json.put("algorithm", obj.getAlgorithm());
        }
        if (obj.getBuffer() != null) {
            json.put("buffer", obj.getBuffer());
        }
        json.put("certificate", obj.isCertificate());
        if (obj.getId() != null) {
            json.put("id", obj.getId());
        }
        if (obj.getPublicKey() != null) {
            json.put("publicKey", obj.getPublicKey());
        }
        if (obj.getSecretKey() != null) {
            json.put("secretKey", obj.getSecretKey());
        }
        json.put("symmetric", obj.isSymmetric());
    }
}
