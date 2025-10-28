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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.validation;

import io.gravitee.am.gateway.handler.oauth2.resources.request.TokenRequestFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * Handler for validating resource parameters in OAuth2 token requests according to RFC 8707.
 * This handler validates that requested resources are recognized by the authorization server.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class TokenRequestResourceValidationHandler implements Handler<RoutingContext> {

	private final ResourceValidationService resourceValidationService;
	private final Domain domain;
	private final TokenRequestFactory tokenRequestFactory = new TokenRequestFactory();

	public TokenRequestResourceValidationHandler(ResourceValidationService resourceValidationService, Domain domain) {
		this.resourceValidationService = resourceValidationService;
		this.domain = domain;
	}

	@Override
	public void handle(RoutingContext context) {
		// Build a normalized TokenRequest using the factory (consistent with codebase patterns)
		final TokenRequest tokenRequest = tokenRequestFactory.create(context);
		final Client client = context.get(CLIENT_CONTEXT_KEY);

		// Validate resources
		resourceValidationService.validate(tokenRequest, domain, client)
				.subscribe(
						() -> {
							log.debug("Resource validation successful for token request");
							context.next();
						},
						error -> {
							log.debug("Resource validation failed for token request: {}", error.getMessage());
							context.fail(error);
						}
				);
	}
}
