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
package io.gravitee.am.service.utils;

import io.gravitee.am.model.Client;

import java.util.*;
import java.util.stream.Collectors;

import static io.gravitee.am.common.oauth2.GrantType.AUTHORIZATION_CODE;
import static io.gravitee.am.common.oauth2.GrantType.IMPLICIT;
import static io.gravitee.am.common.oauth2.ResponseType.CODE;
import static io.gravitee.am.common.oauth2.ResponseType.TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ResponseTypeUtils {

    private static final Set<String> VALID_RESPONSE_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            CODE, TOKEN, ID_TOKEN
    )));

    /**
     * Throw InvalidClientMetadataException if null or empty, or contains unknown response types.
     * @param responseTypes Array of response_type to validate.
     */
    public static boolean isValidResponseType(List<String> responseTypes) {
        if (responseTypes == null || responseTypes.isEmpty()) {
            return false;
        }

        for (String responseType : responseTypes) {
            if (!isValidResponseType(responseType)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Throw InvalidClientMetadataException if null or contains unknown response types.
     * @param responseType String to response_type validate.
     */
    public static boolean isValidResponseType(String responseType) {
        return VALID_RESPONSE_TYPES.contains(responseType);
    }

    /**
     * Before Dynamic Client Registration feature, there were no response type settings on Access Management UI & API.
     * In order to avoid breaking changes for legacy users, we'll add by default all response type possible
     * according to selected grant_type :
     * authorization_code  : add code
     * implicit            : add token and id_token
     *
     * @param client Client to analyse.
     * @return Client updated Client
     */
    public static Client applyDefaultResponseType(Client client) {
        Set responseType = applyDefaultResponseType(client.getAuthorizedGrantTypes());
        client.setResponseTypes((List<String>)responseType.stream().collect(Collectors.toList()));
        return client;
    }

    public static Set<String> applyDefaultResponseType(List<String> grantTypeList) {
        Set<String> grantTypes = new HashSet(grantTypeList);
        Set<String> responseType = new HashSet();

        //If grant_type contains authorization_code, response_type must contains code
        if (grantTypes.contains(AUTHORIZATION_CODE)) {
            responseType.add(CODE);
        }

        //If grant_type contains implicit, response_type must contains token or id_token
        if (grantTypes.contains(IMPLICIT)) {
            responseType.add(ID_TOKEN);
            responseType.add(TOKEN);
        }

        return responseType;
    }

    public static boolean isImplicitFlow(String responseType) {
        return responseType!=null && Arrays.stream(responseType.split("\\s")).allMatch(type -> type.equals(TOKEN) || type.equals(ID_TOKEN));
    }

    public static boolean isHybridFlow(String responseType) {
        return (CODE_ID_TOKEN.equals(responseType) || CODE_TOKEN.equals(responseType) || CODE_ID_TOKEN_TOKEN.equals(responseType));
    }

    public static boolean requireNonce(String responseType) {
        return responseType!=null && (isHybridFlow(responseType) || ID_TOKEN.equals(responseType) || ID_TOKEN_TOKEN.equals(responseType));
    }
}
