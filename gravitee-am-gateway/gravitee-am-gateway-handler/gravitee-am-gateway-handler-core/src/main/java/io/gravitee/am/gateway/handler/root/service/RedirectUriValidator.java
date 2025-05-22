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
package io.gravitee.am.gateway.handler.root.service;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.RedirectMismatchException;
import io.gravitee.am.common.jwt.TokenPurpose;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.oidc.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.getOAuthParameter;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

@Slf4j
@RequiredArgsConstructor
public class RedirectUriValidator {
    private final CheckMethod checkMethod;

    public interface CheckMethod {
        void check(String redirectUri, List<String> registeredUris);
    }

    public void validate(Client client, String requestedRedirectUri) {
        validate(client.getRedirectUris(), requestedRedirectUri, null);
    }

    public void validate(List<String> registeredClientRedirectUris, String requestedRedirectUri, TokenPurpose operation) {

        final boolean redirectUriRequired = requiresRedirectUri(operation);

        final boolean hasRegisteredClientRedirectUris = registeredClientRedirectUris != null && !registeredClientRedirectUris.isEmpty();
        final boolean hasRequestedRedirectUri = requestedRedirectUri != null && !requestedRedirectUri.isEmpty();

        // if no requested redirect_uri and no registered client redirect_uris
        // throw invalid request exception
        if (redirectUriRequired && !hasRegisteredClientRedirectUris && !hasRequestedRedirectUri) {
            throw new InvalidRequestException("A redirect_uri must be supplied");
        }
        // if no requested redirect_uri and more than one registered client redirect_uris
        // throw invalid request exception
        if (redirectUriRequired && !hasRequestedRedirectUri && (registeredClientRedirectUris != null && registeredClientRedirectUris.size() > 1)) {
            throw new InvalidRequestException("Unable to find suitable redirect_uri, a redirect_uri must be supplied");
        }

        if(redirectUriRequired && hasRequestedRedirectUri){
            try {
                // use UriBuilder to sanitize the uri so non urlEncoded character will be encoded
                // to avoid URISyntaxException due to the space in the scope parameter value
                URI uri = UriBuilder.fromURIString(requestedRedirectUri).build();
                if(uri.getUserInfo() != null){
                    throw new RedirectMismatchException(String.format("The redirect_uri [ %s ] MUST NOT contain userInfo part", requestedRedirectUri));
                }
            } catch (URISyntaxException ex){
                // the URI is invalid, only log the error to avoid regression
                // URI white list will reject the value if necessary
                log.warn("The redirect_uri [{}] has syntax error: {}", requestedRedirectUri, ex.getMessage());
            }
        }

        // if requested redirect_uri doesn't match registered client redirect_uris
        // throw redirect mismatch exception
        if (hasRequestedRedirectUri && hasRegisteredClientRedirectUris) {
            checkMethod.check(requestedRedirectUri, registeredClientRedirectUris);
        }
    }

    private boolean requiresRedirectUri(TokenPurpose operation) {
        /* "reset password" or "registration confirmation" link user receives doesn't contain a redirect_uri,
           because unlike other in flows - it's not used. If the uri is provided for any reason we still should
           validate it - but we can't require it to be present. */
        return operation != TokenPurpose.RESET_PASSWORD && operation != TokenPurpose.REGISTRATION_CONFIRMATION;
    }
}
