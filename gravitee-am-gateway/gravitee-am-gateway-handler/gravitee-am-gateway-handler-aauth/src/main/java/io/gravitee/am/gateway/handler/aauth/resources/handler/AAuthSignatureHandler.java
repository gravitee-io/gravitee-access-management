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
package io.gravitee.am.gateway.handler.aauth.resources.handler;

import io.gravitee.am.gateway.handler.aauth.signing.AAuthSignatureVerifier;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vert.x handler that verifies HTTP Message Signatures per the AAUTH protocol spec.
 * On success, stores the {@link VerificationResult} in the routing context as "aauth.verification".
 * On failure, returns 401 with the appropriate Signature-Error header.
 * When no signature headers are present at all, returns 401 with
 * AAuth-Requirement: requirement=pseudonym.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthSignatureHandler implements Handler<RoutingContext> {

    public static final String AAUTH_VERIFICATION_CONTEXT_KEY = "aauth.verification";

    private final AAuthSignatureVerifier verifier;

    @Override
    public void handle(RoutingContext ctx) {
        // Check if any signature headers are present
        if (!hasSignatureHeaders(ctx)) {
            ctx.response()
                    .setStatusCode(401)
                    .putHeader("AAuth-Requirement", "requirement=pseudonym")
                    .end();
            return;
        }

        try {
            byte[] bodyBytes = ctx.body() != null && ctx.body().buffer() != null
                    ? ctx.body().buffer().getBytes() : null;
            VerificationResult result = verifier.verify(ctx.request(), bodyBytes);
            ctx.put(AAUTH_VERIFICATION_CONTEXT_KEY, result);
            ctx.next();
        } catch (SignatureVerificationException e) {
            log.debug("HTTP Message Signature verification failed: {}", e.getErrorCode());
            ctx.response()
                    .setStatusCode(401)
                    .putHeader("Signature-Error", e.toSignatureErrorHeader())
                    .end();
        }
    }

    private boolean hasSignatureHeaders(RoutingContext ctx) {
        return ctx.request().getHeader("Signature") != null
                || ctx.request().getHeader("Signature-Input") != null
                || ctx.request().getHeader("Signature-Key") != null;
    }
}
