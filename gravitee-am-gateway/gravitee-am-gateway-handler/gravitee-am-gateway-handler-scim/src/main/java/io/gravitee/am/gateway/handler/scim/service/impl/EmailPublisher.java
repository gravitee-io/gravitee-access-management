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
package io.gravitee.am.gateway.handler.scim.service.impl;

import io.gravitee.am.gateway.handler.common.email.EmailContainer;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class EmailPublisher implements FlowableOnSubscribe<EmailContainer> {

    private FlowableEmitter<EmailContainer> emitter;

    @Override
    public void subscribe(@NonNull FlowableEmitter<EmailContainer> emitter) throws Throwable {
        log.info("EmailPublisher has been subscribed");
        // call serialize() to follow the sequential recommendation of the Emitter interface
        this.emitter = emitter.serialize();
    }

    public void submit(EmailContainer container) {
        log.debug("Submitting user {} for email notification", container.user().getId());
        if (emitter != null && !emitter.isCancelled() && StringUtils.hasText(container.user().getEmail())) {
            this.emitter.onNext(container);
        } else {
            log.warn("EmailPublisher has been cancelled or email is missing, user {} will not receive email notification", container.user().getId());
        }
    }

    public void cancel() {
        if (emitter != null) {
            emitter.onComplete();
        }
    }

}
