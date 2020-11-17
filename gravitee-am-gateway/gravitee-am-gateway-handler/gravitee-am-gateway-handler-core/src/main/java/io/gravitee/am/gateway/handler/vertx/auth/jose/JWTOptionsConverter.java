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
package io.gravitee.am.gateway.handler.vertx.auth.jose;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link JWTOptions}.
 * NOTE: This class has been automatically generated from the {@link JWTOptions} original class using Vert.x codegen.
 */
// TODO to remove when updating to vert.x 4
public class JWTOptionsConverter {


    public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, JWTOptions obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
                case "algorithm":
                    if (member.getValue() instanceof String) {
                        obj.setAlgorithm((String)member.getValue());
                    }
                    break;
                case "audience":
                    if (member.getValue() instanceof JsonArray) {
                        java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
                        ((Iterable<Object>)member.getValue()).forEach( item -> {
                            if (item instanceof String)
                                list.add((String)item);
                        });
                        obj.setAudience(list);
                    }
                    break;
                case "audiences":
                    if (member.getValue() instanceof JsonArray) {
                        ((Iterable<Object>)member.getValue()).forEach( item -> {
                            if (item instanceof String)
                                obj.addAudience((String)item);
                        });
                    }
                    break;
                case "expiresInMinutes":
                    if (member.getValue() instanceof Number) {
                        obj.setExpiresInMinutes(((Number)member.getValue()).intValue());
                    }
                    break;
                case "expiresInSeconds":
                    if (member.getValue() instanceof Number) {
                        obj.setExpiresInSeconds(((Number)member.getValue()).intValue());
                    }
                    break;
                case "header":
                    if (member.getValue() instanceof JsonObject) {
                        obj.setHeader(((JsonObject)member.getValue()).copy());
                    }
                    break;
                case "ignoreExpiration":
                    if (member.getValue() instanceof Boolean) {
                        obj.setIgnoreExpiration((Boolean)member.getValue());
                    }
                    break;
                case "issuer":
                    if (member.getValue() instanceof String) {
                        obj.setIssuer((String)member.getValue());
                    }
                    break;
                case "leeway":
                    if (member.getValue() instanceof Number) {
                        obj.setLeeway(((Number)member.getValue()).intValue());
                    }
                    break;
                case "noTimestamp":
                    if (member.getValue() instanceof Boolean) {
                        obj.setNoTimestamp((Boolean)member.getValue());
                    }
                    break;
                case "permissions":
                    if (member.getValue() instanceof JsonArray) {
                        java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
                        ((Iterable<Object>)member.getValue()).forEach( item -> {
                            if (item instanceof String)
                                list.add((String)item);
                        });
                        obj.setPermissions(list);
                    }
                    break;
                case "scopeDelimiter":
                    break;
                case "scopes":
                    if (member.getValue() instanceof JsonArray) {
                        java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
                        ((Iterable<Object>)member.getValue()).forEach( item -> {
                            if (item instanceof String)
                                list.add((String)item);
                        });
                        obj.setScopes(list);
                    }
                    break;
                case "subject":
                    if (member.getValue() instanceof String) {
                        obj.setSubject((String)member.getValue());
                    }
                    break;
            }
        }
    }

    public static void toJson(JWTOptions obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    public static void toJson(JWTOptions obj, java.util.Map<String, Object> json) {
        if (obj.getAlgorithm() != null) {
            json.put("algorithm", obj.getAlgorithm());
        }
        if (obj.getAudience() != null) {
            JsonArray array = new JsonArray();
            obj.getAudience().forEach(item -> array.add(item));
            json.put("audience", array);
        }
        json.put("expiresInSeconds", obj.getExpiresInSeconds());
        if (obj.getHeader() != null) {
            json.put("header", obj.getHeader());
        }
        json.put("ignoreExpiration", obj.isIgnoreExpiration());
        if (obj.getIssuer() != null) {
            json.put("issuer", obj.getIssuer());
        }
        json.put("leeway", obj.getLeeway());
        json.put("noTimestamp", obj.isNoTimestamp());
        if (obj.getPermissions() != null) {
            JsonArray array = new JsonArray();
            obj.getPermissions().forEach(item -> array.add(item));
            json.put("permissions", array);
        }
        if (obj.getScopeDelimiter() != null) {
            json.put("scopeDelimiter", obj.getScopeDelimiter());
        }
        if (obj.getScopes() != null) {
            JsonArray array = new JsonArray();
            obj.getScopes().forEach(item -> array.add(item));
            json.put("scopes", array);
        }
        if (obj.getSubject() != null) {
            json.put("subject", obj.getSubject());
        }
    }
}
