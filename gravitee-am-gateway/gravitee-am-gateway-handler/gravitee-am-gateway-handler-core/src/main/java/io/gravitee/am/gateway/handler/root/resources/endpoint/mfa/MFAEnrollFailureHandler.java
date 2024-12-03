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

import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.utils.StaticEnvironmentProvider;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.gateway.handler.root.resources.handler.error.AbstractErrorHandler;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;

public class MFAEnrollFailureHandler extends AbstractErrorHandler {
    private static final String ERROR_CODE_VALUE = "enrollment_channel_invalid";
    public MFAEnrollFailureHandler() {
        super(RootProvider.PATH_MFA_ENROLL);
    }

    @Override
    public void doHandle(RoutingContext context) {
        var errorDescription = getErrorDescription(context);

        updateHashValues(context, errorDescription);
        var redirectUrl = getRedirectUrl(context,errorDescription);

        doRedirect(context.response(), redirectUrl);
    }

    private String getRedirectUrl(RoutingContext context, String errorDescription){
        MultiMap queryParams = updateQueryParams(context, errorDescription);
        String path = context.get(CONTEXT_PATH) + errorPage;
        return resolveProxyRequest(context.request(), path, queryParams, true);
    }

    private String getErrorDescription(RoutingContext context){
        return context.failure() == null ? "MFA Enrollment failed for unexpected reason" : context.failure().getMessage();
    }

    private MultiMap updateQueryParams(RoutingContext context, String errorDescription) {
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());
        queryParams.set(ConstantKeys.ERROR_PARAM_KEY, MFA_ENROLL_VALIDATION_FAILED);
        queryParams.set(ConstantKeys.ERROR_CODE_PARAM_KEY, ERROR_CODE_VALUE);
        queryParams.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

        if (context.request().getParam(Parameters.LOGIN_HINT) != null) {
            // encode login_hint parameter (to not replace '+' sign by a space ' ')
            queryParams.set(Parameters.LOGIN_HINT, StaticEnvironmentProvider.sanitizeParametersEncoding() ?
                    UriBuilder.encodeURIComponent(context.request().getParam(Parameters.LOGIN_HINT)) : context.request().getParam(Parameters.LOGIN_HINT));
        }

        return queryParams;
    }

    private void updateHashValues(RoutingContext context, String errorDescription){
        String toHash = MFA_ENROLL_VALIDATION_FAILED + "$" + errorDescription;
        context.session().put(ERROR_HASH, HashUtil.generateSHA256(toHash));
    }

}
