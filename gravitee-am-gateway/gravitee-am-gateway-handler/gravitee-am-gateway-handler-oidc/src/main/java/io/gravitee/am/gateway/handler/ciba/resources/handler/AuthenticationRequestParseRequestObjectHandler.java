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
package io.gravitee.am.gateway.handler.ciba.resources.handler;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.jwt.DefaultJWTParser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;

import static io.gravitee.am.common.utils.ConstantKeys.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestParseRequestObjectHandler implements Handler<RoutingContext> {

    private final RequestObjectService requestObjectService;

    private final Domain domain;

    public AuthenticationRequestParseRequestObjectHandler(RequestObjectService requestObjectService, Domain domain) {
        this.requestObjectService = requestObjectService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        if (context.request().getParam(Parameters.REQUEST) != null) {
            handleRequestObjectValue(context)
                    .subscribe(jwt -> context.next(), context::fail, context::next);
        } else {
            context.next();
        }
    }

    private Maybe<JWT> handleRequestObjectValue(RoutingContext context) {
        final String request = context.request().getParam(Parameters.REQUEST);

        if (request != null) {
            // Ensure that the request_uri is not propagated to the next authorization flow step
            context.request().params().remove(Parameters.REQUEST);

            return requestObjectService
                    .readRequestObject(request, context.get(CLIENT_CONTEXT_KEY), false)
                    .map(jwt -> preserveRequestObject(context, jwt))
                    .flatMap(jwt -> validateRequestObjectClaims(context, jwt))
                    .onErrorResumeNext(error -> {
                        // invalid_request_object isn't specified in the CIBA specification
                        // so catch all exception different from InvalidRequestException and
                        // return convert it in InvalidRequestException
                        if (!(error instanceof InvalidRequestException)) {
                            return Single.error(new InvalidRequestException(error.getMessage()));
                        }

                        return Single.error(error);
                    })
                    .toMaybe();
        } else {
            return Maybe.empty();
        }
    }

    /**
     * Keep the requestObject JWT into the RoutingContext for later use.
     * This is useful to retrieve parameter either from the JWT or from the request params
     *
     * @param context
     * @param jwt
     * @return
     */
    private JWT preserveRequestObject(RoutingContext context, JWT jwt) {
        context.put(REQUEST_OBJECT_KEY, jwt);
        return jwt;
    }

    private Single<JWT> validateRequestObjectClaims(RoutingContext context, JWT jwt) {

        try {
            OpenIDProviderMetadata oidcMeta = context.get(PROVIDER_METADATA_CONTEXT_KEY);
            Client client = context.get(CLIENT_CONTEXT_KEY);

            final JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // aud : The Audience claim MUST contain the value of the Issuer Identifier for the OP, which identifies the Authorization Server as an intended audience.
            final Object aud = claims.getClaim(Claims.aud);
            if (aud == null ||
                    (aud instanceof String && !oidcMeta.getIssuer().equals(aud)) ||
                    (aud instanceof Collection && (((Collection) aud).isEmpty() || !((Collection) aud).stream().anyMatch(oidcMeta.getIssuer()::equals)))
            ) {
                return Single.error(new InvalidRequestException("aud is missing or invalid"));
            }

            // iss : The Issuer claim MUST be the client_id of the OAuth Client.
            final String iss = claims.getStringClaim(Claims.iss);
            if (iss == null || !client.getClientId().equals(iss)) {
                return Single.error(new InvalidRequestException("iss is missing or invalid"));
            }

            // exp : An expiration time that limits the validity lifetime of the signed authentication request.
            final Date exp = claims.getDateClaim(Claims.exp);
            if (exp == null) {
                return Single.error(new InvalidRequestException("exp is missing"));
            }
            DefaultJWTParser.evaluateExp(exp.getTime() / 1000, Instant.now(), 0);

            // iat : The time at which the signed authentication request was created.
            final Date iat = claims.getDateClaim(Claims.iat);
            if (iat == null) {
                return Single.error(new InvalidRequestException("iat is missing"));
            }

            // nbf : The time before which the signed authentication request is unacceptable.
            final Date nbf = claims.getDateClaim(Claims.nbf);
            if (nbf == null) {
                return Single.error(new InvalidRequestException("nbf is missing"));
            }
            DefaultJWTParser.evaluateNbf(nbf.getTime() / 1000, Instant.now(), 0);

            // jti : A unique identifier for the signed authentication request.
            if (claims.getStringClaim(Claims.jti) == null) {
                return Single.error(new InvalidRequestException("jti is missing"));
            }

        } catch (ParseException e) {
            return Single.error(new InvalidRequestException("Unable to read claims in the request parameter"));
        } catch (PrematureJWTException e) {
            return Single.error(new InvalidRequestException("nbf is invalid"));
        } catch (ExpiredJWTException e) {
            return Single.error(new InvalidRequestException("jwt has expired"));
        }

        return Single.just(jwt);
    }
}
