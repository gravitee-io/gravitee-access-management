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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

/** The three CIBA Core §7.1 hint types. Built to the RFC, not to what the gateway plumbs today:
 *  {@code idTokenHint} is carried through the seam even though the notifier boundary does not yet
 *  populate it inbound, so no future extension is required when the gateway catches up. Exactly one
 *  is expected to be non-blank per request (enforced in the provider after decoration). */
public record CibaHints(String loginHint, String loginHintToken, String idTokenHint) {
    public CibaHints withLoginHint(String v) { return new CibaHints(v, loginHintToken, idTokenHint); }
    public CibaHints withLoginHintToken(String v) { return new CibaHints(loginHint, v, idTokenHint); }
    public CibaHints withIdTokenHint(String v) { return new CibaHints(loginHint, loginHintToken, v); }
}
