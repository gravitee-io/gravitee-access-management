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
package io.gravitee.am.gateway.handler.oauth2.service.el;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.UserInfoClaim;
import io.gravitee.gateway.api.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
public class ExecutionContextTokenEnhancer {

    public void enhanceToken(JWT jwt, TokenTypeHint hint, List<TokenClaim> customClaims, ExecutionContext executionContext) {
        if (customClaims != null && !customClaims.isEmpty()) {
            customClaims
                    .stream()
                    .filter(tokenClaim -> hint.equals(tokenClaim.getTokenType()))
                    .forEach(tokenClaim -> {
                            String claimName = tokenClaim.getClaimName();
                            String claimExpression = tokenClaim.getClaimValue();
                            evaluateAndUpdate(jwt, executionContext, claimName, claimExpression);
                    });
        }
    }

    public void enhanceToken(JWT jwt, List<UserInfoClaim> customClaims, ExecutionContext executionContext) {
        if (customClaims != null && !customClaims.isEmpty()) {
            customClaims
                    .forEach(tokenClaim -> {
                        String claimName = tokenClaim.getClaimName();
                        String claimExpression = tokenClaim.getClaimValue();
                        evaluateAndUpdate(jwt, executionContext, claimName, claimExpression);
                    });
        }
    }

    private void evaluateAndUpdate(JWT jwt, ExecutionContext executionContext, String claimName, String claimExpression){
        try {
            Object extValue = (claimExpression != null) ? executionContext.getTemplateEngine().getValue(claimExpression, Object.class) : null;
            if (extValue != null) {
                if (Claims.AUD.equals(claimName) && (extValue instanceof String[] || extValue instanceof List)) {
                    var audiences = new LinkedHashSet<>();
                    audiences.add(jwt.getAud()); // make sure the client_id is the first entry of the aud array
                    audiences.addAll(extValue instanceof List ? (List) extValue : List.of((String[]) extValue)); // Set will remove duplicate client_id if any
                    var jsonArray = new JSONArray();
                    jsonArray.addAll(audiences);
                    jwt.put(claimName, jsonArray);
                } else {
                    jwt.put(claimName, extValue);
                }
            }
        } catch (Exception ex) {
            log.debug("An error occurs while parsing expression language : {}", claimExpression, ex);
        }
    }
}
