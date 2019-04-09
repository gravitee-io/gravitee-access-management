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

import static io.gravitee.am.common.oauth2.GrantType.*;
import static io.gravitee.am.common.oauth2.ResponseType.CODE;
import static io.gravitee.am.common.oauth2.ResponseType.TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class GrantTypeUtils {

    private static final Set<String> VALID_GRANT_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            AUTHORIZATION_CODE, IMPLICIT, REFRESH_TOKEN, CLIENT_CREDENTIALS, PASSWORD, JWT_BEARER, SAML2_BEARER
    )));

    /**
     * Throw InvalidClientMetadataException if null or empty, or contains unknown grant types.
     * @param grantTypes Array of grant_type to validate.
     */
    public static boolean isValidGrantType(List<String> grantTypes) {
        if(grantTypes==null || grantTypes.isEmpty()) {
            return false;
        }

        for(String grantType:grantTypes) {
            if(!isValidGrantType(grantType)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if grant type is known/supported.
     * @param grantType String grant_type to validate.
     */
    public static boolean isValidGrantType(String grantType) {
        return VALID_GRANT_TYPES.contains(grantType);
    }

    /**
     * As specified in openid specs, ensure correspondence between response_type and grant_type.
     * Here is the following table lists response_type --> expected grant_type.
     * code                : authorization_code
     * id_token            : implicit
     * token id_token      : implicit
     * code id_token       : authorization_code, implicit
     * code token          : authorization_code, implicit
     * code token id_token : authorization_code, implicit
     *
     * @param client Client to analyse.
     */
    public static Client completeGrantTypeCorrespondance(Client client) {
        boolean updatedGrantType = false;

        Set responseType = client.getResponseTypes() != null ? new HashSet<>(client.getResponseTypes()) : new HashSet();
        Set grantType = client.getAuthorizedGrantTypes() != null ? new HashSet<>(client.getAuthorizedGrantTypes()) : new HashSet();

        //If response type contains "code", then grant_type must contains "authorization_code"
        if(responseType.contains(CODE) && !grantType.contains(AUTHORIZATION_CODE)) {
            grantType.add(AUTHORIZATION_CODE);
            updatedGrantType=true;
        }

        //If response type contains "token" or "id_token", then grant_type must contains "implicit"
        if((responseType.contains(TOKEN)||responseType.contains(ID_TOKEN)) && !grantType.contains(IMPLICIT)) {
            grantType.add(IMPLICIT);
            updatedGrantType=true;
        }

        //If grant_type contains authorization_code, response_type must contains code
        if(grantType.contains(AUTHORIZATION_CODE) && !responseType.contains(CODE)) {
            grantType.remove(AUTHORIZATION_CODE);
            updatedGrantType=true;
        }

        //If grant_type contains implicit, response_type must contains token or id_token
        if(grantType.contains(IMPLICIT) && (!responseType.contains(TOKEN) && !responseType.contains(ID_TOKEN))) {
            grantType.remove(IMPLICIT);
            updatedGrantType=true;
        }

        //Finally in case of bad client status (no response/grant type) reset to default values...
        if(responseType.isEmpty() && grantType.isEmpty()) {
            client.setResponseTypes(Client.DEFAULT_RESPONSE_TYPES);
            client.setAuthorizedGrantTypes(Client.DEFAULT_GRANT_TYPES);
        }

        //if grant type list has been modified, then update it.
        else if(updatedGrantType) {
            client.setAuthorizedGrantTypes((List<String>)grantType.stream().collect(Collectors.toList()));
        }

        return client;
    }
}
