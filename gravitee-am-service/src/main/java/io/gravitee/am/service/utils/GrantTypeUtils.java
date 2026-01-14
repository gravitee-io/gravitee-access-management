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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Single;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.common.oauth2.GrantType.AUTHORIZATION_CODE;
import static io.gravitee.am.common.oauth2.GrantType.CIBA_GRANT_TYPE;
import static io.gravitee.am.common.oauth2.GrantType.CLIENT_CREDENTIALS;
import static io.gravitee.am.common.oauth2.GrantType.IMPLICIT;
import static io.gravitee.am.common.oauth2.GrantType.JWT_BEARER;
import static io.gravitee.am.common.oauth2.GrantType.PASSWORD;
import static io.gravitee.am.common.oauth2.GrantType.REFRESH_TOKEN;
import static io.gravitee.am.common.oauth2.GrantType.TOKEN_EXCHANGE;
import static io.gravitee.am.common.oauth2.GrantType.UMA;
import static io.gravitee.am.common.oauth2.ResponseType.CODE;
import static io.gravitee.am.common.oauth2.ResponseType.TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GrantTypeUtils {

    private static final String AM_V2_VERSION = "AM_V2_VERSION";
    private static final String EXTENSION_GRANT_SEPARATOR = "~";
    private static final Set<String> SUPPORTED_GRANT_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            AUTHORIZATION_CODE, IMPLICIT, REFRESH_TOKEN, CLIENT_CREDENTIALS, PASSWORD, JWT_BEARER, UMA, CIBA_GRANT_TYPE, TOKEN_EXCHANGE//, DEVIDE_CODE, SAML2_BEARER
    )));

    /**
     * <pre>
     * Check:
     *  - grant types are null or empty, or contains unknown grant types.
     *  - refresh_token does not come with authorization_code, password or client_credentials grant.
     *  - client_credentials grant come with another grant that require user authentication.
     * </pre>
     * @param application Application with grant_type to validate.
     * @return Single client or error
     */
    public static Single<Application> validateGrantTypes(Application application) {
        // no application to check, continue
        if (application==null) {
            return Single.error(new InvalidClientMetadataException("No application to validate grant"));
        }

        // no application settings to check, continue
        if (application.getSettings() == null) {
            return Single.just(application);
        }

        // no application oauth settings to check, continue
        if (application.getSettings().getOauth() == null) {
            return Single.just(application);
        }

        // Each security domain can have multiple extension grant with the same grant_type
        // we must split the client authorized grant types to get the real grant_type value
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        List<String> formattedClientGrantTypes = oAuthSettings.getGrantTypes() == null ? null : oAuthSettings.getGrantTypes().stream().map(str -> str.split(EXTENSION_GRANT_SEPARATOR)[0]).collect(Collectors.toList());
        if(!isSupportedGrantType(formattedClientGrantTypes)) {
            return Single.error(new InvalidClientMetadataException("Missing or invalid grant type."));
        }

        //Ensure correspondance between response & grant types.
        completeGrantTypeCorrespondance(application);

        //refresh_token are not allowed for all grant types...
        Set<String> grantTypeSet = Collections.unmodifiableSet(new HashSet<>(oAuthSettings.getGrantTypes()));
        if(grantTypeSet.contains(REFRESH_TOKEN)) {
            //Hybrid is not managed yet and AM does not support refresh token for client_credentials for now...
            List<String> allowedRefreshTokenGrant = Arrays.asList(AUTHORIZATION_CODE, PASSWORD, JWT_BEARER);
            //return true if there is no element in common
            if(Collections.disjoint(formattedClientGrantTypes, allowedRefreshTokenGrant)) {
                return Single.error(new InvalidClientMetadataException(
                        REFRESH_TOKEN+" grant type must be associated with one of "+String.join(", ",allowedRefreshTokenGrant)
                ));
            }
        }

        /*
         * Uncomment when ready to setup a "non expert mode" on the AM user interface"
         * It is not recommended to mix client and user authentication within the same application.
         * (Aka client_credentials and authorization_code, implicit or password...)
        if(grantTypeSet.contains(CLIENT_CREDENTIALS)) {
            //If client_credentials come with at least one of belows grant
            if(!Collections.disjoint(client.getAuthorizedGrantTypes(),Arrays.asList(AUTHORIZATION_CODE, IMPLICIT, PASSWORD, HYBRID, DEVIDE_CODE))) {
                return Single.error(new InvalidClientMetadataException(
                        CLIENT_CREDENTIALS+" must not be associated with another grant that imply user authentication"
                ));
            }
        }
        */

        return Single.just(application);
    }

    public static List<String> getSupportedGrantTypes() {
        return Collections.unmodifiableList(SUPPORTED_GRANT_TYPES.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * @param grantTypes Array of grant_type to validate.
     */
    public static boolean isSupportedGrantType(List<String> grantTypes) {
        if(grantTypes==null || grantTypes.isEmpty()) {
            return false;
        }

        //Check grant types are all known
        for(String grantType:grantTypes) {
            if(!isSupportedGrantType(grantType)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if grant type is known/supported.
     * @param grantType String grant_type to validate.
     */
    public static boolean isSupportedGrantType(String grantType) {
        return SUPPORTED_GRANT_TYPES.contains(grantType);
    }

    /**
     * <pre>
     * According to the specification: https://tools.ietf.org/html/rfc6749#section-10.6
     * Authorization Server MUST require public clients and SHOULD require confidential clients to register their redirection URIs.
     * confidential clients are clients that can keep their credentials secrets, ex:
     *  - web application (using a web server to save their credentials) : authorization_code
     *  - server application (considering credentials saved on a server as safe) : client_credentials
     * by opposition to confidential, public clients are clients than can not keep their credentials as secret, ex:
     *  - Single Page Application : implicit
     *  - Native mobile application : authorization_code
     * Because mobile and web application use the same grant, we force redirect_uri only for implicit grant.
     * </pre>
     * @param grantTypes Array of grant_type
     * @return true if at least one of the grant type included in the array require a redirect_uri.
     */
    public static boolean isRedirectUriRequired(List<String> grantTypes) {
        List<String> requireRedirectUri = Arrays.asList(IMPLICIT);//, HYBRID); Add Hybrid once supported...
        //return true if there's no grant type matching
        return grantTypes!=null && !Collections.disjoint(grantTypes, requireRedirectUri);
    }

    /**
     * As specified in openid specs, ensure correspondence between response_type and grant_type.
     * Here is the following table lists response_type --> expected grant_type.
     * code                : authorization_code
     * id_token            : implicit
     * token id_token      : implicit
     * code id_token       : authorization_code, implicit
     * code token          : authorization_code, implicit
     * code id_token token : authorization_code, implicit
     *
     * @param application Application to analyse.
     */
    public static Application completeGrantTypeCorrespondance(Application application) {
        boolean updatedGrantType = false;

        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        Set responseType = oAuthSettings.getResponseTypes() != null ? new HashSet<>(oAuthSettings.getResponseTypes()) : new HashSet();
        Set grantType = oAuthSettings.getGrantTypes() != null ? new HashSet<>(oAuthSettings.getGrantTypes()) : new HashSet();

        //If response type contains "code", then grant_type must contains "authorization_code"
        if(mustHaveAuthorizationCode(responseType) && !grantType.contains(AUTHORIZATION_CODE)) {
            grantType.add(AUTHORIZATION_CODE);
            updatedGrantType=true;
        }

        //If response type contains "token" or "id_token", then grant_type must contains "implicit"
        if(mustHaveImplicit(responseType) && !grantType.contains(IMPLICIT)) {
            grantType.add(IMPLICIT);
            updatedGrantType=true;
        }

        //If grant_type contains authorization_code, response_type must contains code
        if(grantType.contains(AUTHORIZATION_CODE) && !mustHaveAuthorizationCode(responseType)) {
            grantType.remove(AUTHORIZATION_CODE);
            updatedGrantType=true;
        }

        //If grant_type contains implicit, response_type must contains token or id_token
        if(grantType.contains(IMPLICIT) && !mustHaveImplicit(responseType)) {
            grantType.remove(IMPLICIT);
            updatedGrantType=true;
        }

        //Finally in case of bad client status (no response/grant type) reset to default values...
        if(responseType.isEmpty() && grantType.isEmpty()) {
            oAuthSettings.setResponseTypes(Client.DEFAULT_RESPONSE_TYPES);
            oAuthSettings.setGrantTypes(Client.DEFAULT_GRANT_TYPES);
        }

        //if grant type list has been modified, then update it.
        else if(updatedGrantType) {
            oAuthSettings.setGrantTypes((List<String>)grantType.stream().collect(Collectors.toList()));
        }

        return application;
    }

    public static Client completeGrantTypeCorrespondance(Client client) {
        boolean updatedGrantType = false;

        Set responseType = client.getResponseTypes() != null ? new HashSet<>(client.getResponseTypes()) : new HashSet();
        Set grantType = client.getAuthorizedGrantTypes() != null ? new HashSet<>(client.getAuthorizedGrantTypes()) : new HashSet();

        //If response type contains "code", then grant_type must contains "authorization_code"
        if(mustHaveAuthorizationCode(responseType) && !grantType.contains(AUTHORIZATION_CODE)) {
            grantType.add(AUTHORIZATION_CODE);
            updatedGrantType=true;
        }

        //If response type contains "token" or "id_token", then grant_type must contains "implicit"
        if(mustHaveImplicit(responseType) && !grantType.contains(IMPLICIT)) {
            grantType.add(IMPLICIT);
            updatedGrantType=true;
        }

        //If grant_type contains authorization_code, response_type must contains code
        if(grantType.contains(AUTHORIZATION_CODE) && !mustHaveAuthorizationCode(responseType)) {
            grantType.remove(AUTHORIZATION_CODE);
            updatedGrantType=true;
        }

        //If grant_type contains implicit, response_type must contains token or id_token
        if(grantType.contains(IMPLICIT) && !mustHaveImplicit(responseType)) {
            grantType.remove(IMPLICIT);
            updatedGrantType=true;
        }

        // If grant_type contains client_credentials, remove refresh_token flow, only for old clients created by the upgrader
        if (AM_V2_VERSION.equals(client.getSoftwareVersion()) && grantType.contains(CLIENT_CREDENTIALS) && grantType.contains(REFRESH_TOKEN) && grantType.size() == 2) {
                grantType.remove(REFRESH_TOKEN);
                updatedGrantType = true;
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

    private static boolean mustHaveAuthorizationCode(Set<String> responseType) {
        return responseType.contains(CODE) ||
                responseType.contains(CODE_TOKEN) ||
                responseType.contains(CODE_ID_TOKEN) ||
                responseType.contains(CODE_ID_TOKEN_TOKEN);
    }

    private static boolean mustHaveImplicit(Set<String> responseType) {
        return responseType.contains(TOKEN) ||
                responseType.contains(ID_TOKEN) ||
                responseType.contains(ID_TOKEN_TOKEN);
    }
}
