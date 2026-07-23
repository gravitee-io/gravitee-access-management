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
package io.gravitee.am.gateway.handler.ciba.service.request;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.authdevice.notifier.api.IdentityProviderDependent;
import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.oauth2.ExpiredLoginHintTokenException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.service.request.AbstractRequestResolver;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Single;
import net.minidev.json.JSONObject;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import static io.gravitee.am.jwt.DefaultJWTParser.evaluateExp;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class CibaAuthenticationRequestResolver extends AbstractRequestResolver<CibaAuthenticationRequest> {

    private final Domain domain;
    private final JWSService jwsService;
    private final JWKService jwkService;
    private final UserGatewayService userService;
    private final SubjectManager subjectManager;
    private final AuthenticationDeviceNotifierManager deviceNotifierManager;

    public CibaAuthenticationRequestResolver(Domain domain, JWSService jwsService, JWKService jwkService, UserGatewayService userService, SubjectManager subjectManager, AuthenticationDeviceNotifierManager deviceNotifierManager) {
        this.domain = domain;
        this.jwsService = jwsService;
        this.jwkService = jwkService;
        this.userService = userService;
        this.subjectManager = subjectManager;
        this.deviceNotifierManager = deviceNotifierManager;
    }

    public Single<CibaAuthenticationRequest> resolve(CibaAuthenticationRequest authRequest, Client client) {
        return resolveAuthorizedScopes(authRequest, client, null).flatMap(req -> {
            if (!StringUtils.isEmpty(req.getIdTokenHint())) {
                return parseJwt(req.getIdTokenHint()).flatMap(jwt -> {
                    if (jwt instanceof SignedJWT) {
                        return validateIdTokenHint(authRequest, (SignedJWT)jwt);
                    } else {
                        return Single.error(new InvalidRequestException("id_token_hint must be signed"));
                    }
                });
            } else if (!StringUtils.isEmpty(req.getLoginHintToken())) {
                return parseJwt(req.getLoginHintToken()).flatMap(jwt -> {
                    // Specification doesn't specify if this token have to be signed
                    if (jwt instanceof SignedJWT) {
                        return verifyLoginHintTokenSignature((SignedJWT)jwt, client)
                                .flatMap(verifiedJwt -> validateLoginHintToken(authRequest, verifiedJwt));
                    } else {
                        return validateLoginHintToken(authRequest, jwt);
                    }
                });
            } else {
                // login_hint is provided (look for username or email)

                // A structured / IdP-ready hint (e.g. an iss_sub JSON object
                // {"format":"iss_sub","iss":"...","sub":"..."}) is NOT a local
                // username/email. The user-search filter rejects any
                // value containing the injection-guard chars " \ ; { } $ ^, so feeding such a
                // hint to findByCriteria throws IllegalArgumentException and the request 500s.
                // Guard BEFORE the local lookup: if the hint is not locally resolvable, skip
                // findByCriteria entirely. When federation is on, relay the hint verbatim with
                // NO subject — identity is established downstream at completion
                // (validateUserResponse). Otherwise reject cleanly as a 400.
                if (!isLocallyResolvableHint(authRequest.getLoginHint())) {
                    if (isFederatedHintResolutionEnabled()) {
                        return Single.just(authRequest);
                    }
                    return Single.error(new InvalidRequestException("Invalid hint"));
                }

                final FilterCriteria criteria = new FilterCriteria();

                criteria.setQuoteFilterValue(true);
                criteria.setFilterName(authRequest.getLoginHint().contains("@") ? "email" : "username");
                criteria.setFilterValue(authRequest.getLoginHint());
                criteria.setOperator("eq");

                return userService.findByCriteria(criteria).map(users -> {
                    if (users.size() != 1) {
                        // Federated: an unresolved hint is authenticated downstream at completion
                        // (validateUserResponse); carry it as-is with NO subject. Flag off => stock (throw).
                        if (users.isEmpty() && isFederatedHintResolutionEnabled()) {
                            return authRequest;
                        }
                        log.warn("login_hint match multiple users or no one");
                        throw new InvalidRequestException("Invalid hint");
                    }
                    authRequest.setUser(users.getFirst());
                    authRequest.setSubject(users.getFirst().getId());
                    return authRequest;
                });

            }
        });
    }

    // The user-search filter (FilterCriteriaParser) rejects any value containing these
    // injection-guard chars. A hint that contains any of them cannot be a local
    // username/email and must not be passed to findByCriteria.
    private static final String FILTER_HOSTILE_CHARS = "\"\\;{}$^";

    private static boolean isLocallyResolvableHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        for (int i = 0; i < FILTER_HOSTILE_CHARS.length(); i++) {
            if (hint.indexOf(FILTER_HOSTILE_CHARS.charAt(i)) >= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isFederatedHintResolutionEnabled() {
        var notifiers = domain.getOidc().getCibaSettings().getDeviceNotifiers();
        if (notifiers == null || notifiers.isEmpty()) return false;
        var provider = deviceNotifierManager.getAuthDeviceNotifierProvider(notifiers.get(0).getId());
        return provider instanceof IdentityProviderDependent;
    }

    private Single<JWT> parseJwt(String hint) {
        return Single.fromCallable(() -> {
            try {
                return JWTParser.parse(hint);
            } catch (ParseException e) {
                throw new InvalidRequestException("hint is not a valid JWT");
            }
        });
    }

    private Single<CibaAuthenticationRequest> validateIdTokenHint(CibaAuthenticationRequest authRequest, SignedJWT signedJwt) {
        // NOTE: federated hint resolution is intentionally NOT applied to id_token_hint —
        // an id_token_hint already identifies a local subject, so it must resolve locally or fail.
        // id_token_hint is the id_token generate by the OP for the client, so we use the JWKS provided by the domain.
        return jwkService.getKeys()
                .flatMapMaybe(jwks -> jwkService.getKey(jwks, signedJwt.getHeader().getKeyID()))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("JWK not found for id_token_hint")))
                .filter(jwk -> jwsService.isValidSignature(signedJwt, jwk))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("Invalid signature fo id_token_hint")))
                .flatMap(jwk -> {
                    try {
                        final Date expirationTime = signedJwt.getJWTClaimsSet().getExpirationTime();
                        if (expirationTime != null) {
                            evaluateExp(expirationTime.toInstant().getEpochSecond(), Instant.now(), 0);
                        }
                        return subjectManager.findUserBySub(new io.gravitee.am.common.jwt.JWT(signedJwt.getJWTClaimsSet().getClaims())).map(user -> {
                            authRequest.setUser(user);
                            authRequest.setSubject(user.getId());
                            return authRequest;
                        }).toSingle();
                    } catch (ExpiredJWTException e) {
                        return Single.error(new InvalidRequestException("id_token_hint expired"));
                    }
                });
    }

    private Single<JWT> verifyLoginHintTokenSignature(SignedJWT signedJwt, Client client) {
        // As the login_hint_token is provided by the client, we use the Client JWKS to validate the signature.
        // contrary to the id_token_hint that is the id_token generated by the OP for the client.
        return jwkService.getKeys(client)
                .flatMap(jwks -> jwkService.getKey(jwks, signedJwt.getHeader().getKeyID()))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("JWK not found for login_token_hint")))
                .filter(jwk -> jwsService.isValidSignature(signedJwt, jwk))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("Invalid signature fo login_token_hint")))
                .flatMap(__ -> Single.just(signedJwt));
    }

    private Single<CibaAuthenticationRequest> validateLoginHintToken(CibaAuthenticationRequest authRequest, JWT jwt) {
        try {
            final Date expirationTime = jwt.getJWTClaimsSet().getExpirationTime();
            if (expirationTime != null) {
                evaluateExp(expirationTime.toInstant().getEpochSecond(), Instant.now(), 0);
            }

            final JSONObject subIdObject = new JSONObject(jwt.getJWTClaimsSet().getJSONObjectClaim("sub_id"));
            /*
                sub_id is an object specifying the field identifying the user (through format entry)
                Supported format : email and username
                {
                  "sub_id": {
                    "format": "email",
                    "email": "user@acme.fr"
                  }
                }
             */
            final FilterCriteria criteria = new FilterCriteria();
            criteria.setQuoteFilterValue(false);
            final String field = subIdObject.getAsString("format");
            if (!"email".equals(field) && !"username".equals(field)) {
                return Single.error(new InvalidRequestException("Invalid hint, only email and username are supported"));
            }
            final String hintValue = subIdObject.getAsString(field);
            criteria.setFilterName(field);
            criteria.setFilterValue(hintValue);

            return userService.findByCriteria(criteria).flatMap(users -> {
                if (users.size() != 1) {
                    // Federated: an unresolved hint is authenticated downstream at completion
                    // (validateUserResponse); carry it as-is with NO subject. Flag off => stock (error).
                    if (users.isEmpty() && isFederatedHintResolutionEnabled()) {
                        return Single.just(authRequest);
                    }
                    log.warn("login_hint_token match multiple users or no one");
                    return Single.error(new InvalidRequestException("Invalid hint"));
                }
                authRequest.setUser(users.getFirst());
                authRequest.setSubject(users.getFirst().getId());
                return Single.just(authRequest);
            });

        } catch (ExpiredJWTException e) {
            return Single.error(new ExpiredLoginHintTokenException("login_token_hint expired"));
        } catch (ParseException e) {
            // should never happen
            log.warn("login_hint_token can't be read", e);
            return Single.error(new ExpiredLoginHintTokenException("invalid login_token_hint"));
        }
    }
}
