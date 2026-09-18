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
package io.gravitee.am.gateway.handler.oauth2.service.binding;

import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.el.TemplateEngine;
import io.gravitee.el.exceptions.ExpressionEvaluationException;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.expression.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingException.Reason.NO_MATCH;
import static io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingException.Reason.SEVERAL_MATCHES;
import static io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingException.Reason.UNUSABLE_CRITERIA;

@CustomLog
@RequiredArgsConstructor
public class UserBindingResolver {

    private static final String TOKEN_VARIABLE = "token";

    private final UserGatewayService userGatewayService;

    public Single<User> resolve(List<UserBindingCriterion> criteria, Map<String, Object> claims) {
        return Single.fromCallable(() -> buildCriteria(criteria, claims))
                .flatMap(userGatewayService::findByCriteria)
                .flatMap(users -> {
                    if (users.isEmpty()) {
                        return Single.error(new UserBindingException(NO_MATCH, "No domain user found for token binding"));
                    }
                    if (users.size() > 1) {
                        return Single.error(new UserBindingException(SEVERAL_MATCHES, "Multiple domain users match token binding"));
                    }
                    return userGatewayService.enhance(users.getFirst());
                });
    }

    private static FilterCriteria buildCriteria(List<UserBindingCriterion> criteria, Map<String, Object> claims) {
        if (claims == null) {
            throw new UserBindingException(UNUSABLE_CRITERIA, "Token binding: no claims available for evaluation");
        }

        TemplateEngine engine = TemplateEngine.templateEngine();
        engine.getTemplateContext().setVariable(TOKEN_VARIABLE, claims);

        List<FilterCriteria> components = new ArrayList<>();
        for (UserBindingCriterion c : criteria) {
            String attribute = c.getAttribute();
            String expression = c.getExpression();
            if (StringUtils.isBlank(attribute) || StringUtils.isBlank(expression)) {
                log.warn("Token binding: skipping criterion with blank attribute='{}' or expression='{}'", attribute, expression);
                continue;
            }
            Object value;
            try {
                value = engine.getValue(expression, Object.class);
            } catch (ExpressionEvaluationException | ParseException e) {
                log.debug("Token binding: EL evaluation failed for expression '{}'", expression, e);
                throw new UserBindingException(UNUSABLE_CRITERIA, "Token binding: expression evaluation failed: " + e.getMessage());
            }
            if (value == null) {
                throw new UserBindingException(UNUSABLE_CRITERIA, "Token binding: expression '" + expression + "' evaluated to null");
            }
            String filterValue = value.toString().trim();
            if (filterValue.isEmpty()) {
                throw new UserBindingException(UNUSABLE_CRITERIA, "Token binding: expression '" + expression + "' evaluated to empty value");
            }
            FilterCriteria eq = new FilterCriteria();
            eq.setFilterName(attribute.trim());
            eq.setFilterValue(filterValue);
            eq.setOperator("eq");
            eq.setQuoteFilterValue(true);
            components.add(eq);
        }

        if (components.isEmpty()) {
            throw new UserBindingException(UNUSABLE_CRITERIA, "Token binding: no valid criteria (attribute and expression required)");
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
