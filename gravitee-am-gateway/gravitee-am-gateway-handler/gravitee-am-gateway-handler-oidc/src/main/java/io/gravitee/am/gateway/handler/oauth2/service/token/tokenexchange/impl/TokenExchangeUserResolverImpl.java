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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeUserResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.el.TemplateContext;
import io.gravitee.el.TemplateEngine;
import io.gravitee.el.exceptions.ExpressionEvaluationException;
import io.reactivex.rxjava3.core.Single;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves an external JWT subject to a domain user using trusted issuer user binding criteria.
 * EL context variable {@code token} holds the subject token claims.
 *
 * @author GraviteeSource Team
 */
public class TokenExchangeUserResolverImpl implements TokenExchangeUserResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenExchangeUserResolverImpl.class);
    private static final String TOKEN_VARIABLE = "token";

    @Override
    public Single<Optional<User>> resolve(ValidatedToken subjectToken,
                                          TrustedIssuer trustedIssuer,
                                          UserGatewayService userGatewayService) {
        if (trustedIssuer == null || !trustedIssuer.isUserBindingEnabled()) {
            return Single.just(Optional.empty());
        }
        List<UserBindingCriterion> criteria = trustedIssuer.getUserBindingCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return Single.just(Optional.empty());
        }

        return Single.fromCallable(() -> buildCriteria(subjectToken, criteria))
                .flatMap(filterCriteria -> userGatewayService.findByCriteria(filterCriteria)
                        .map(users -> {
                            if (users.isEmpty()) {
                                throw new InvalidGrantException("No domain user found for token binding");
                            }
                            if (users.size() > 1) {
                                throw new InvalidGrantException("Multiple domain users match token binding");
                            }
                            return Optional.of(users.getFirst());
                        }));
    }

    /**
     * Build FilterCriteria (AND of attribute=value) by evaluating each criterion expression with token claims in context.
     */
    private FilterCriteria buildCriteria(ValidatedToken subjectToken, List<UserBindingCriterion> criteria) {
        Map<String, Object> claims = subjectToken.getClaims();
        if (claims == null) {
            throw new InvalidGrantException("Token binding: no claims available for evaluation");
        }

        TemplateEngine engine = TemplateEngine.templateEngine();
        TemplateContext context = engine.getTemplateContext();
        context.setVariable(TOKEN_VARIABLE, claims);

        List<FilterCriteria> components = new ArrayList<>();
        for (UserBindingCriterion c : criteria) {
            String attribute = c.getAttribute();
            String expression = c.getExpression();
            if (StringUtils.isBlank(attribute) || StringUtils.isBlank(expression)) {
                LOGGER.warn("Token binding: skipping criterion with blank attribute='{}' or expression='{}'", attribute, expression);
                continue;
            }
            Object value;
            try {
                value = engine.getValue(expression, Object.class);
            } catch (ExpressionEvaluationException e) {
                LOGGER.debug("Token binding: EL evaluation failed for expression '{}'", expression, e);
                throw new InvalidGrantException("Token binding: expression evaluation failed: " + e.getMessage());
            }
            if (value == null) {
                throw new InvalidGrantException("Token binding: expression '" + expression + "' evaluated to null");
            }
            String filterValue = value.toString().trim();
            if (filterValue.isEmpty()) {
                throw new InvalidGrantException("Token binding: expression '" + expression + "' evaluated to empty value");
            }
            FilterCriteria eq = new FilterCriteria();
            eq.setFilterName(attribute.trim());
            eq.setFilterValue(filterValue);
            eq.setOperator("eq");
            eq.setQuoteFilterValue(true);
            components.add(eq);
        }

        if (components.isEmpty()) {
            throw new InvalidGrantException("Token binding: no valid criteria (attribute and expression required)");
        }

        if (components.size() == 1) {
            return components.get(0);
        }
        FilterCriteria and = new FilterCriteria();
        and.setOperator("and");
        and.setFilterComponents(components);
        return and;
    }
}
