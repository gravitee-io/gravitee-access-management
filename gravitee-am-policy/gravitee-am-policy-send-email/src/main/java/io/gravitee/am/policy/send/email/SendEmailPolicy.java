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
package io.gravitee.am.policy.send.email;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.common.email.EmailBuilder;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.policy.send.email.configuration.SendEmailPolicyConfiguration;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequest;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SendEmailPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailPolicy.class);

    private SendEmailPolicyConfiguration configuration;

    public SendEmailPolicy(SendEmailPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext context, PolicyChain policyChain) {
        LOGGER.debug("Start SendEmail.onRequest");
        Completable.defer(() -> {
            try {
                EmailService emailService = context.getComponent(EmailService.class);
                Email email = new EmailBuilder()
                        .template(configuration.getTemplate())
                        .from(configuration.getFrom())
                        .fromName(configuration.getFromName())
                        .to(configuration.getTo().split("\\s*,\\s*"))
                        .subject(configuration.getSubject())
                        .content(configuration.getContent())
                        .params(context.getAttributes())
                        .build();
                emailService.send(email);
                return Completable.complete();
            } catch (Exception ex) {
                return Completable.error(ex);
            }
        })
        .doOnError(e -> LOGGER.error("An error has occurred when sending an email via the SendEmail policy", e))
        .subscribeOn(Schedulers.io())
        .subscribe();

        // continue
        policyChain.doNext(request, response);
    }
}
