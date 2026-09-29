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
package io.gravitee.am.gateway.handler.root.resources.endpoint.rememberdevice;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.CustomLog;

import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_REMEMBER_DEVICE_CONSENT_TIME;
import static io.gravitee.am.common.utils.ConstantKeys.ETAG_DEVICE_ID;
import static java.util.Optional.ofNullable;

@CustomLog
public class RememberDeviceEtagEndpoint implements Handler<RoutingContext> {

    static final String ETAG_ISSUED_CLAIM = "am_etag";
    static final String CACHE_CONTROL = "private, no-cache";
    static final String BODY = "/* remember device */";

    private final DeviceIdentifierManager deviceIdentifierManager;
    private final JWTService jwtService;
    private final String rememberDeviceCookieName;

    public RememberDeviceEtagEndpoint(DeviceIdentifierManager deviceIdentifierManager, JWTService jwtService, String rememberDeviceCookieName) {
        this.deviceIdentifierManager = deviceIdentifierManager;
        this.jwtService = jwtService;
        this.rememberDeviceCookieName = rememberDeviceCookieName;
    }

    @Override
    public void handle(RoutingContext context) {
        final Client client = context.get(CLIENT_CONTEXT_KEY);
        final var settings = rememberDeviceSettings(client);
        if (client == null || !settings.isActive() || !deviceIdentifierManager.useEtagBasedDeviceIdentifier(client)) {
            context.response()
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .setStatusCode(204)
                    .end();
            return;
        }

        final var cookie = context.request().getCookie(rememberDeviceCookieName);
        Single.zip(
                        verify(parseEtag(context.request().getHeader(HttpHeaders.IF_NONE_MATCH)), client),
                        verify(cookie == null ? null : cookie.getValue(), client),
                        (etag, cookieToken) -> resolve(context, client, settings, etag, cookieToken))
                .flatMap(single -> single)
                .subscribe(
                        ignored -> {},
                        error -> {
                            log.warn("Unable to resolve remember device ETag for clientID '{}'", client.getClientId(), error);
                            if (!context.response().ended()) {
                                context.response()
                                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                        .setStatusCode(204)
                                        .end();
                            }
                        });
    }

    private Single<Boolean> resolve(RoutingContext context, Client client, RememberDeviceSettings settings, Optional<Token> etag, Optional<Token> cookie) {
        if (cookie.isPresent()) {
            rememberInSession(context, cookie.get());
            if (etag.isPresent() && etag.get().raw().equals(cookie.get().raw())) {
                return notModified(context, etag.get());
            }
            log.debug("Remember device ETag primed from cookie for clientID '{}'", client.getClientId());
            return ok(context, cookie.get().raw());
        }
        if (etag.isPresent()) {
            rememberInSession(context, etag.get());
            if (!etag.get().jwt().containsKey(ETAG_ISSUED_CLAIM)) {
                log.debug("Remember device cookie restored from ETag for clientID '{}'", client.getClientId());
                restoreCookie(context, etag.get());
            }
            return notModified(context, etag.get());
        }
        return issue(client, settings)
                .flatMap(token -> {
                    rememberInSession(context, token);
                    log.debug("Remember device ETag issued for clientID '{}'", client.getClientId());
                    return ok(context, token.raw());
                });
    }

    private Single<Token> issue(Client client, RememberDeviceSettings settings) {
        final var jwt = new JWT();
        final long now = System.currentTimeMillis() / 1000;
        jwt.setIat(now);
        jwt.setExp(now + ofNullable(settings.getExpirationTimeSeconds()).orElse(DEFAULT_REMEMBER_DEVICE_CONSENT_TIME));
        jwt.setJti(UUID.randomUUID().toString());
        jwt.put(ETAG_ISSUED_CLAIM, true);
        return jwtService.encode(jwt, client).map(raw -> new Token(raw, jwt));
    }

    private Single<Optional<Token>> verify(String raw, Client client) {
        if (isNullOrEmpty(raw)) {
            return Single.just(Optional.empty());
        }
        return jwtService.decodeAndVerify(raw, client, JWTService.TokenType.SESSION)
                .map(jwt -> Optional.of(new Token(raw, jwt)))
                .onErrorReturn(error -> {
                    log.debug("Remember device token validation fails for clientID '{}'", client.getClientId(), error);
                    return Optional.empty();
                });
    }

    private void restoreCookie(RoutingContext context, Token token) {
        final long maxAge = token.jwt().getExp() - System.currentTimeMillis() / 1000;
        if (maxAge > 0) {
            context.response().addCookie(Cookie.cookie(rememberDeviceCookieName, token.raw())
                    .setHttpOnly(true)
                    .setMaxAge(maxAge));
        }
    }

    private void rememberInSession(RoutingContext context, Token token) {
        if (context.session() != null) {
            context.session().put(ETAG_DEVICE_ID, token.jwt().getJti());
        }
    }

    private Single<Boolean> notModified(RoutingContext context, Token token) {
        return context.response()
                .putHeader(HttpHeaders.ETAG, quote(token.raw()))
                .putHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .setStatusCode(304)
                .end()
                .toSingleDefault(true);
    }

    private Single<Boolean> ok(RoutingContext context, String raw) {
        return context.response()
                .putHeader(HttpHeaders.ETAG, quote(raw))
                .putHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/javascript")
                .setStatusCode(200)
                .end(BODY)
                .toSingleDefault(true);
    }

    static String parseEtag(String ifNoneMatch) {
        if (isNullOrEmpty(ifNoneMatch)) {
            return null;
        }
        var value = ifNoneMatch.split(",")[0].trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() || "*".equals(value) ? null : value;
    }

    private static String quote(String raw) {
        return "\"" + raw + "\"";
    }

    private static RememberDeviceSettings rememberDeviceSettings(Client client) {
        return ofNullable(client)
                .map(Client::getMfaSettings)
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    private record Token(String raw, JWT jwt) {
    }
}
