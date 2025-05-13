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
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.common.exception.mfa.SendChallengeException;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.utils.UsernameHelper;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.gateway.handler.root.resources.handler.error.AbstractErrorHandler;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_FAILED;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class MFAChallengeFailureHandler extends AbstractErrorHandler {
    private static final String ERROR_CODE_VALUE = "send_challenge_failed";

    private final AuthenticationFlowContextService authenticationFlowContextService;

    public MFAChallengeFailureHandler(AuthenticationFlowContextService authenticationFlowContextService) {
        super(RootProvider.PATH_ERROR);
        this.authenticationFlowContextService = authenticationFlowContextService;
    }

    @Override
    public void doHandle(RoutingContext routingContext) {
        handleException(routingContext, routingContext.failure());
    }

    private void handleException(RoutingContext context, Throwable throwable) {
        String errorDescription = throwable == null ? "MFA Challenge failed for unexpected reason" : throwable.getMessage();
        final MultiMap queryParams = updateQueryParams(context, errorDescription);
        String uri;
        if (throwable instanceof SendChallengeException) {
            uri =  UriBuilderRequest.resolveProxyRequest(context.request(), context.request().uri(), queryParams, true);
        } else {
            logoutUser(context);
            uri = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/login", queryParams, true);
        }
        String toHash = MFA_CHALLENGE_FAILED + "$" + errorDescription;
        context.session().put(ERROR_HASH, HashUtil.generateSHA256(toHash));

        doRedirect(context.response(), uri);
    }

    private MultiMap updateQueryParams(RoutingContext context, String errorDescription) {
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());
        queryParams.set(ConstantKeys.ERROR_PARAM_KEY, MFA_CHALLENGE_FAILED);
        queryParams.set(ConstantKeys.ERROR_CODE_PARAM_KEY, ERROR_CODE_VALUE);
        queryParams.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        UsernameHelper.escapeUsernameParam(queryParams, Parameters.LOGIN_HINT);
        UsernameHelper.escapeUsernameParam(queryParams, io.gravitee.am.common.oauth2.Parameters.USERNAME);

        return queryParams;
    }

    private void logoutUser(RoutingContext context) {
        if (context.user() != null) {
            // clear AuthenticationFlowContext. data of this context have a TTL, so we can fire and forget in case on error.
            authenticationFlowContextService.clearContext(context.session().get(ConstantKeys.TRANSACTION_ID_KEY))
                    .doOnError(error -> log.info("Deletion of authentication flow data fails '{}'", error.getMessage()))
                    .subscribe();

            context.clearUser();
            context.session().destroy();
        }
    }
}
