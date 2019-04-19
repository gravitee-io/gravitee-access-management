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
package io.gravitee.am.gateway.handler.oidc.service.request;

import io.gravitee.am.gateway.handler.oidc.exception.ClaimsRequestSyntaxException;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class ClaimsRequestResolver {

    /**
     * @param claims the claims parameter value is represented in an OAuth 2.0 request as UTF-8 encoded JSON
     * @return Decoded JSON/POJO claims object
     * @throws ClaimsRequestSyntaxException
     */
    public ClaimsRequest resolve(String claims) throws ClaimsRequestSyntaxException {
        try {
            // The claims parameter value is represented in an OAuth 2.0 request as UTF-8 encoded JSON
            JsonObject claimsValue = new JsonObject(claims);
            ClaimsRequest claimsRequest = new ClaimsRequest();
            // set userinfo parameter
            claimsRequest.setUserInfoClaims(resolveClaimsRequest(claimsValue, ClaimsRequest.USERINFO));
            // set id_token parameter
            claimsRequest.setIdTokenClaims(resolveClaimsRequest(claimsValue, ClaimsRequest.ID_TOKEN));
            return claimsRequest;
        } catch (Exception e) {
            throw new ClaimsRequestSyntaxException(e);
        }
    }

    /**
     * The top-level members of the Claims request JSON object are: userinfo and id_token
     * @param claimsRequest claims request
     * @param parameterName userinfo or id_token parameter
     * @return top level members of Claims request
     */
    private Map<String, Object> resolveClaimsRequest(JsonObject claimsRequest, String parameterName) {
        JsonObject claimsParameterValue = claimsRequest.getJsonObject(parameterName);
        if (claimsParameterValue != null) {
            Map<String, Object> claimsParameter = new HashMap<>();
            claimsParameterValue.iterator().forEachRemaining(entry -> resolveIndividualClaimsRequests(entry.getKey(), entry.getValue(), claimsParameter));
            return claimsParameter;
        }
        return null;
    }

    /**
     * The userinfo and id_token members of the claims request both are JSON objects with the names of the individual Claims being requested as the member names.
     * The member values MUST be one of the following:
     *  - null
     *  - JSON Object
     * @param claimName requested claim name
     * @param claimValue requested claim value (null, essential, value, values)
     * @param claimsParameter requested claims
     */
    private void resolveIndividualClaimsRequests(String claimName, Object claimValue, Map<String, Object> claimsParameter) {
        // two possible value (null or JSONObject)
        if (claimValue == null) {
            claimsParameter.put(claimName, claimValue);
        } else {
            if (claimValue instanceof JsonObject) {
                JsonObject individualClaim = (JsonObject) claimValue;
                individualClaim.iterator().forEachRemaining(entry -> {
                    // 3 possible value (essential, value and values)
                    switch (entry.getKey()) {
                        case ClaimsRequest.ESSENTIAL:
                            // essential must be a boolean value
                            if (entry.getValue() != null && entry.getValue() instanceof Boolean) {
                                // no value to check (essential) set to null
                                claimsParameter.put(claimName, new ClaimsRequest.Essential((boolean) entry.getValue()));
                            }
                            break;
                        case ClaimsRequest.VALUE:
                        case ClaimsRequest.VALUES:
                            // TODO
                            break;
                        default:
                            // Any members used that are not understood MUST be ignored.
                    }
                });
            }
        }

    }
}
