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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.common.jwt.CertificateInfo;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.IdJagTarget;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.springframework.util.ObjectUtils.isEmpty;

public final class OAuth2RequestParams {

    public static final String SIGNING_CERTIFICATE_ID = "SIGNING_CERTIFICATE_ID";
    public static final String SIGNING_CERTIFICATE_NAME = "SIGNING_CERTIFICATE_NAME";

    private OAuth2RequestParams() {
    }

    public static Map<String, Object> of(OAuth2Request oAuth2Request) {
        return of(oAuth2Request, null);
    }

    public static Map<String, Object> of(OAuth2Request oAuth2Request, CertificateInfo certificateInfo) {
        var params = new HashMap<String, Object>();
        params.put(Parameters.GRANT_TYPE.toUpperCase(), oAuth2Request.getGrantType());
        params.put(Parameters.RESPONSE_TYPE.toUpperCase(), oAuth2Request.getResponseType());

        if (!isEmpty(oAuth2Request.getScopes())) {
            params.put(Parameters.SCOPE.toUpperCase(), String.join(" ", oAuth2Request.getScopes()));
        }

        if (!isEmpty(oAuth2Request.getResources())) {
            params.put(Parameters.RESOURCE.toUpperCase(), String.join(" ", oAuth2Request.getResources()));
        }

        if (certificateInfo != null) {
            params.put(SIGNING_CERTIFICATE_ID, certificateInfo.certificateId());
            params.put(SIGNING_CERTIFICATE_NAME, certificateInfo.certificateAlias());
        }

        if (oAuth2Request.getConfirmationMethodJkt() != null) {
            params.put(Parameters.DPOP_JKT.toUpperCase(), oAuth2Request.getConfirmationMethodJkt());
        }

        if (GrantType.TOKEN_EXCHANGE.equals(oAuth2Request.getGrantType())) {
            addTokenExchangeParams(params, oAuth2Request);
        }

        return params;
    }

    private static void addTokenExchangeParams(Map<String, Object> params, OAuth2Request oAuth2Request) {
        putFirstPresent(params, Parameters.REQUESTED_TOKEN_TYPE,
                oAuth2Request.getIssuedTokenType(), requested(oAuth2Request, Parameters.REQUESTED_TOKEN_TYPE));

        putFirstPresent(params, Parameters.SUBJECT_TOKEN, oAuth2Request.getSubjectTokenId());

        putFirstPresent(params, Parameters.SUBJECT_TOKEN_TYPE,
                oAuth2Request.getSubjectTokenType(), requested(oAuth2Request, Parameters.SUBJECT_TOKEN_TYPE));

        IdJagTarget idJagTarget = oAuth2Request.getIdJagTarget();
        putFirstPresent(params, Parameters.AUDIENCE,
                idJagTarget == null ? null : idJagTarget.audience(), requested(oAuth2Request, Parameters.AUDIENCE));
        putFirstPresent(params, Parameters.RESOURCE,
                idJagTarget == null ? null : idJagTarget.resource(), joined(oAuth2Request.getResources()));

        if (oAuth2Request.isDelegation()) {
            putFirstPresent(params, Parameters.ACTOR_TOKEN, oAuth2Request.getActorTokenId());
            putFirstPresent(params, Parameters.ACTOR_TOKEN_TYPE, oAuth2Request.getActorTokenType());
        }
    }

    private static void putFirstPresent(Map<String, Object> params, String parameter, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null) {
                params.put(parameter.toUpperCase(), candidate);
                return;
            }
        }
    }

    private static String requested(OAuth2Request oAuth2Request, String parameter) {
        return oAuth2Request.parameters() == null ? null : oAuth2Request.parameters().getFirst(parameter);
    }

    private static String joined(Set<String> values) {
        return isEmpty(values) ? null : String.join(" ", values);
    }
}
