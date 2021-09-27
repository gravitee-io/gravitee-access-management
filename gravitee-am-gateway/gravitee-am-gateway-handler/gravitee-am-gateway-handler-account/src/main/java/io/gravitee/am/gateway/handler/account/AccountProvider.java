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
package io.gravitee.am.gateway.handler.account;

import io.gravitee.am.gateway.handler.account.resources.AccountEndpointHandler;
import io.gravitee.am.gateway.handler.account.resources.AccountFactorsEndpointHandler;
import io.gravitee.am.gateway.handler.account.resources.AccountWebAuthnCredentialsEndpointHandler;
import io.gravitee.am.gateway.handler.account.resources.util.AccountRoutes;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * @author Donald Courtney (donald.courtney at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private Router router;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OAuth2AuthProvider oAuth2AuthProvider;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FactorManager factorManager;

    @Autowired
    private CorsHandler corsHandler;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (isSelfServiceAccountEnabled()) {
            // Create the account router
            final Router accountRouter = Router.router(vertx);

            // CORS handler
            accountRouter.route().handler(corsHandler);

            // Account resources are OAuth 2.0 secured
            OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider);
            oAuth2AuthHandler.extractToken(true);
            oAuth2AuthHandler.extractClient(true);
            accountRouter.route().handler(oAuth2AuthHandler);

            // Account profile routes
            final AccountEndpointHandler accountHandler = new AccountEndpointHandler(accountService);
            accountRouter.get(AccountRoutes.PROFILE.getRoute()).handler(accountHandler::getUser).handler(accountHandler::getProfile);
            accountRouter.put(AccountRoutes.PROFILE.getRoute()).handler(BodyHandler.create()).handler(accountHandler::getUser).handler(accountHandler::updateProfile);
            accountRouter.get(AccountRoutes.ACTIVITIES.getRoute()).handler(accountHandler::getUser).handler(accountHandler::getActivity);
            accountRouter.get(AccountRoutes.CHANGE_PASSWORD.getRoute()).handler(accountHandler::redirectForgotPassword);

            // Account factors routes
            AccountFactorsEndpointHandler accountFactorsEndpointHandler =
                    new AccountFactorsEndpointHandler(accountService, factorManager, applicationContext);

            accountRouter.get(AccountRoutes.FACTORS.getRoute())
                    .handler(accountHandler::getUser)
                    .handler(accountFactorsEndpointHandler::listEnrolledFactors);
            accountRouter.get(AccountRoutes.FACTORS_CATALOG.getRoute())
                    .handler(accountHandler::getUser)
                    .handler(accountFactorsEndpointHandler::listAvailableFactors);
            accountRouter.get(AccountRoutes.FACTORS_BY_ID.getRoute())
                    .handler(accountHandler::getUser)
                    .handler(accountFactorsEndpointHandler::getEnrolledFactor);
            accountRouter.get(AccountRoutes.FACTORS_OTP_QR.getRoute())
                    .handler(accountHandler::getUser)
                    .handler(accountFactorsEndpointHandler::getEnrolledFactorQrCode);
            accountRouter.delete(AccountRoutes.FACTORS_BY_ID.getRoute())
                    .handler(accountHandler::getUser)
                    .handler(accountFactorsEndpointHandler::removeFactor);
            accountRouter.post(AccountRoutes.FACTORS.getRoute())
                    .handler(BodyHandler.create())
                    .handler(accountHandler::getUser)
                    .handler(accountFactorsEndpointHandler::enrollFactor);
            accountRouter.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                    .handler(BodyHandler.create())
                    .handler(accountHandler::getUser)
                    .handler(accountFactorsEndpointHandler::verifyFactor);

            // WebAuthn credentials routes
            AccountWebAuthnCredentialsEndpointHandler accountWebAuthnCredentialsEndpointHandler =
                    new AccountWebAuthnCredentialsEndpointHandler(accountService);
            accountRouter.get(AccountRoutes.WEBAUTHN_CREDENTIALS.getRoute())
                    .handler(accountHandler::getUser)
                    .handler(accountWebAuthnCredentialsEndpointHandler::listEnrolledWebAuthnCredentials);
            accountRouter.get(AccountRoutes.WEBAUTHN_CREDENTIALS_BY_ID.getRoute())
                    .handler(accountHandler::getUser)
                    .handler(accountWebAuthnCredentialsEndpointHandler::getEnrolledWebAuthnCredential);

            // error handler
            accountRouter.route().failureHandler(new ErrorHandler());

            // mount account router
            router.mountSubRouter(path(), accountRouter);
        }
    }

    @Override
    public String path() {
        return "/account";
    }

    private boolean isSelfServiceAccountEnabled() {
        return domain.getSelfServiceAccountManagementSettings() != null &&
                domain.getSelfServiceAccountManagementSettings().isEnabled();
    }
}
