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

import io.gravitee.am.gateway.handler.account.resources.account.AccountEndpointHandler;
import io.gravitee.am.gateway.handler.account.services.AccountManagementUserService;
import io.gravitee.am.gateway.handler.account.services.ActivityAuditService;
import io.gravitee.am.gateway.handler.account.resources.account.util.AccountRoutes;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.springframework.beans.factory.annotation.Autowired;

public class AccountProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {
    @Override
    public String path() {
        return "/account";
    }

    @Autowired
    private Vertx vertx;

    @Autowired
    private Domain domain;

    @Autowired
    private Router router;

    @Autowired
    private UserService userService;

    @Autowired
    private FactorService factorService;

    @Autowired
    private ActivityAuditService activityAuditService;

    @Autowired
    private AccountManagementUserService accountManagementUserService;

    @Autowired
    private OAuth2AuthProvider oAuth2AuthProvider;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (isSelfServiceAccountEnabled()) {
            final Router accountRouter = Router.router(vertx);
            final AccountEndpointHandler accountHandler = new AccountEndpointHandler(userService, factorService, activityAuditService, accountManagementUserService, domain);
            OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider);
            oAuth2AuthHandler.extractToken(true);
            oAuth2AuthHandler.extractClient(true);
            accountRouter.route().handler(oAuth2AuthHandler);

            // user consent routes
            accountRouter.get(AccountRoutes.INDEX.getRoute()).handler(accountHandler::getUser).handler(accountHandler::getAccount);
            accountRouter.get(AccountRoutes.STATIC_ASSETS.getRoute()).handler(accountHandler::getUser).handler(accountHandler::getAsset);
            accountRouter.get(AccountRoutes.PROFILE.getRoute()).handler(accountHandler::getUser).handler(accountHandler::getProfile);
            accountRouter.put(AccountRoutes.PROFILE.getRoute()).handler(BodyHandler.create()).handler(accountHandler::getUser).handler(accountHandler::updateProfile);
            accountRouter.get(AccountRoutes.FACTORS.getRoute()).handler(accountHandler::getUser).handler(accountHandler::getUserFactors);
            accountRouter.put(AccountRoutes.FACTORS.getRoute()).handler(BodyHandler.create()).handler(accountHandler::getUser).handler(accountHandler::updateUserFactors);
            accountRouter.get(AccountRoutes.ACTIVITIES.getRoute()).handler(accountHandler::getUser).handler(accountHandler::getActivity);
            accountRouter.get(AccountRoutes.CHANGE_PASSWORD.getRoute()).handler(accountHandler::redirectForgotPassword);
            // error handler
            accountRouter.route().failureHandler(new ErrorHandler());
            router.mountSubRouter(path(), accountRouter);
        }
    }

    private boolean isSelfServiceAccountEnabled() {
        return domain.getSelfServiceAccountManagementSettings() != null && domain.getSelfServiceAccountManagementSettings().isEnabled();
    }
}
