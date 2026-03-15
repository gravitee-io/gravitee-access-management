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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.action;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.flow.ExecutionPredicate;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.policy.UserActionPolicy;
import io.gravitee.am.gateway.handler.common.utils.RedirectUrlResolver;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerResponse;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DOMAIN_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.gateway.handler.root.RootProvider.*;
import static io.gravitee.common.http.HttpHeaders.LOCATION;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserActionEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(UserActionEndpoint.class);
    private static final String FLOW_KEY = "flow";
    private static final String ACTION_KEY = "action";
    private static final String UA_STATE_KEY = "ua_state";
    private final RedirectUrlResolver redirectUrlResolver = new RedirectUrlResolver();
    private final FlowManager flowManager;
    private final ExecutionContextFactory executionContextFactory;
    private final JWTService jwtService;
    private final Domain domain;

    public UserActionEndpoint(TemplateEngine templateEngine,
                              FlowManager flowManager,
                              ExecutionContextFactory executionContextFactory,
                              JWTService jwtService,
                              Domain domain) {
        super(templateEngine);
        this.flowManager = flowManager;
        this.executionContextFactory = executionContextFactory;
        this.jwtService = jwtService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderPage(routingContext);
                break;
            case "POST":
                redirect(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderPage(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final String uaState = request.getParam(UA_STATE_KEY);

        // resolve ua_state
        resolveUAState(uaState, client)
                .flatMap(jwt -> {
                    // prepare context

                    // remove sensible client data
                    routingContext.put(CLIENT_CONTEXT_KEY, new ClientProperties(client));
                    // put domain in context data
                    routingContext.put(DOMAIN_CONTEXT_KEY, domain);
                    // put request in context
                    EvaluableRequest evaluableRequest = new EvaluableRequest(new VertxHttpServerRequest(request.getDelegate(), true));
                    routingContext.put(REQUEST_CONTEXT_KEY, evaluableRequest);

                    // put error in context
                    final String error = request.getParam(ERROR_PARAM_KEY);
                    final String errorDescription = request.getParam(ERROR_DESCRIPTION_PARAM_KEY);
                    routingContext.put(ERROR_PARAM_KEY, error);
                    routingContext.put(ERROR_DESCRIPTION_PARAM_KEY, errorDescription);

                    // put parameters in context (backward compatibility)
                    Map<String, String> params = new HashMap<>(evaluableRequest.getParams().toSingleValueMap());
                    params.put(ERROR_PARAM_KEY, error);
                    params.put(ERROR_DESCRIPTION_PARAM_KEY, errorDescription);
                    final String loginHint = routingContext.request().getParam(Parameters.LOGIN_HINT);
                    if (loginHint != null) {
                        params.put(ConstantKeys.USERNAME_PARAM_KEY, loginHint);
                    }
                    routingContext.put(PARAM_CONTEXT_KEY, params);

                    // put actions in context
                    final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
                    routingContext.put(ACTION_KEY, resolveProxyRequest(request, routingContext.get(CONTEXT_PATH) + "/userAction", queryParams, true));
                    final Map<String, Object> data = generateData(routingContext, domain, client);

                    // resolve the template to use
                    String action = (String) jwt.get(ACTION_KEY);
                    String extensionPoint = (String) jwt.get(FLOW_KEY);
                    return getExecutionContext(routingContext)
                            .flatMap(executionContext ->
                                    getUserActionPolicies(ExtensionPoint.valueOf(extensionPoint), client, executionContext)
                                            .flattenAsObservable(policies -> policies)
                                            .filter(policy -> action.equals(policy.getAction()))
                                            .firstOrError()
                                            .map(policy -> Map.entry(data, policy.getTemplate()))
                            );
                })
                .subscribe(
                        result -> this.renderPage(
                                routingContext,
                                result.getKey(),
                                client,
                                result.getValue(),
                                logger,
                                "Unable to render User-Action page"
                        ),
                        ex -> {
                            logger.error("An error has occurred for UserActionPolicy", ex);
                            routingContext.fail(ex);
                        }
                );
    }

    private void redirect(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final String uaState = request.getParam(UA_STATE_KEY);

        // resolve ua_state
        resolveUAState(uaState, client)
            .flatMap(jwt -> {
                    String action = (String) jwt.get(ACTION_KEY);
                    String extensionPoint = (String) jwt.get(FLOW_KEY);
                    return getExecutionContext(routingContext)
                            .flatMap(executionContext ->
                                    getUserActionPolicies(ExtensionPoint.valueOf(extensionPoint), client, executionContext)
                                            .flatMap(policies -> {
                                                int currentIndex = -1;
                                                for (int i = 0; i < policies.size(); i++) {
                                                    if (action.equals(policies.get(i).getAction())) {
                                                        currentIndex = i;
                                                        break;
                                                    }
                                                }
                                                if (currentIndex == -1) {
                                                    return Single.error(
                                                            new NoSuchElementException("No user action policy found for action " + action)
                                                    );
                                                }
                                                UserActionPolicy currentPolicy = policies.get(currentIndex);
                                                int nextIndex = currentIndex + 1;

                                                return currentPolicy.performUserAction(executionContext.request(), executionContext)
                                                        .andThen(Single.defer(() -> {
                                                            // There is a next policy available, set the new ua_state
                                                            if (nextIndex < policies.size()) {
                                                                return createNewUAState(client, extensionPoint, policies.get(nextIndex).getAction())
                                                                        .map(newState -> request.uri().replace("ua_state=" + uaState, "ua_state=" + newState));
                                                            // End of the list reached
                                                            } else {
                                                                return Single.just(resolveRedirectUrl(ExtensionPoint.valueOf(extensionPoint), client, routingContext));
                                                            }
                                                        }));
                                            })
                            );
                })
                .subscribe(
                        redirectUrl ->
                                routingContext.response()
                                        .putHeader(LOCATION, redirectUrl)
                                        .setStatusCode(302)
                                        .end(),
                        routingContext::fail
                );
    }

    private Single<List<UserActionPolicy>> getUserActionPolicies(ExtensionPoint extensionPoint,
                                                                 Client client,
                                                                 ExecutionContext executionContext) {
        return flowManager.findByExtensionPoint(extensionPoint, client, ExecutionPredicate.from(executionContext))
                .map(policies -> policies.stream()
                        .filter(policy -> policy.policyInst() instanceof UserActionPolicy)
                        .map(policy -> (UserActionPolicy) policy.policyInst())
                        .collect(Collectors.toList()));
    }

    private Single<JWT> resolveUAState(String state, Client client) {
        if (state == null) {
            return Single.error(new IllegalArgumentException("Missing ua_state"));
        }
        return jwtService.decodeAndVerify(state, client, JWTService.TokenType.STATE)
                .flatMap(jwt -> {
                    if (jwt.getSub() == null || !jwt.getSub().equals(client.getClientId())) {
                        return Single.error(new IllegalArgumentException("state sub mismatch"));
                    }
                    String flow = (String) jwt.get(FLOW_KEY);
                    try {
                        ExtensionPoint.valueOf(flow);
                    } catch (IllegalArgumentException iae) {
                        return Single.error(new IllegalArgumentException("unknown extension point"));
                    }
                    return Single.just(jwt);
                });
    }

    private Single<ExecutionContext> getExecutionContext(RoutingContext routingContext) {
        return Single.fromCallable(() -> {
            io.vertx.core.http.HttpServerRequest request = routingContext.request().getDelegate();
            Request serverRequest = new VertxHttpServerRequest(request);
            Response serverResponse = new VertxHttpServerResponse(request, serverRequest.metrics());
            ExecutionContext simpleExecutionContext = new SimpleExecutionContext(serverRequest, serverResponse);
            ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);
            // add current context attributes
            executionContext.getAttributes().putAll(getEvaluableAttributes(routingContext));
            return executionContext;
        });
    }

    private Single<String> createNewUAState(Client client, String flow, String newAction) {
        JWT jwt = new JWT();
        jwt.setIat(Instant.now().getEpochSecond());
        jwt.setExp(Instant.now().plusSeconds(300).getEpochSecond()); // 5 minutes validity
        jwt.setDomain(client.getDomain());
        jwt.setSub(client.getClientId());
        jwt.put(FLOW_KEY, flow);
        jwt.put(ACTION_KEY, newAction);
        return jwtService.encode(jwt, client);
    }

    private String resolveRedirectUrl(ExtensionPoint extensionPoint, Client client, RoutingContext routingContext) {
        switch (extensionPoint) {
            // redirect to login page at the end of the login_identifier step
            case POST_LOGIN_IDENTIFIER:
                return redirectUrlResolver.resolveRedirectUrl(routingContext, "/login");
            // replay the initial flow
            case POST_LOGIN,
                 POST_CONSENT,
                 POST_MFA_ENROLLMENT,
                 POST_MFA_CHALLENGE:
                return redirectUrlResolver.resolveRedirectUrl(routingContext);
            // redirect to the register page (or custom one) after the registration process
            case POST_REGISTER: {
                AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
                if (accountSettings != null && accountSettings.getRedirectUriAfterRegistration() != null) {
                    return accountSettings.getRedirectUriAfterRegistration();
                } else {
                    // return to the REGISTER page with a success parameter
                    return redirectUrlResolver.resolveRedirectUrl(routingContext, Map.of(ConstantKeys.SUCCESS_PARAM_KEY, "registration_succeed"), PATH_REGISTER);

                }
            }
            // redirect to the WEBAUTHN_REGISTER_SUCCESS flow if the option is enabled, else replay the flow
            case POST_WEBAUTHN_REGISTER: {
                LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
                if (loginSettings != null
                        && loginSettings.isPasswordlessEnabled()
                        && loginSettings.isPasswordlessDeviceNamingEnabled()) {
                    return redirectUrlResolver.resolveRedirectUrl(routingContext, PATH_WEBAUTHN_REGISTER_SUCCESS);
                } else {
                    return redirectUrlResolver.resolveRedirectUrl(routingContext);
                }
            }
        }
        // by default, we fall back on the original request
        return redirectUrlResolver.resolveRedirectUrl(routingContext);
    }
}
