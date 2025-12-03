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
package io.gravitee.am.service.spring.email;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;

/**
 * SASL Client implementation for OAuth2 (XOAUTH2) authentication.
 *
 * This client implements the XOAUTH2 SASL mechanism as used by
 * Microsoft 365, Gmail, and other OAuth2-enabled SMTP servers.
 *
 * The XOAUTH2 format is:
 * user=<username>\001auth=Bearer <access_token>\001\001
 * where \001 is the ASCII control character (0x01)
 *
 * @author GraviteeSource Team
 */
public class OAuth2SaslClient implements SaslClient {

    private static final String MECHANISM_NAME = "XOAUTH2";
    private final String username;
    private final CallbackHandler callbackHandler;
    private boolean complete = false;

    public OAuth2SaslClient(String username, CallbackHandler callbackHandler) {
        this.username = username;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public String getMechanismName() {
        return MECHANISM_NAME;
    }

    @Override
    public boolean hasInitialResponse() {
        return true;
    }

    @Override
    public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
        if (complete) {
            return new byte[0];
        }

        try {
            // Get username and access token from callbacks
            NameCallback nameCallback = new NameCallback("Username:");
            PasswordCallback passwordCallback = new PasswordCallback("Access Token:", false);

            callbackHandler.handle(new Callback[]{nameCallback, passwordCallback});

            String user = nameCallback.getName();
            String accessToken = new String(passwordCallback.getPassword());

            // Clear the password from memory
            passwordCallback.clearPassword();

            // Build the XOAUTH2 authentication string
            // Format: user=<username>^Aauth=Bearer <access_token>^A^A
            // where ^A is the ASCII control character 0x01
            String authString = String.format("user=%s\001auth=Bearer %s\001\001",
                user, accessToken);

            complete = true;
            return authString.getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new SaslException("Error generating OAuth2 SASL response", e);
        }
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        throw new UnsupportedOperationException("XOAUTH2 does not support security layers");
    }

    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        throw new UnsupportedOperationException("XOAUTH2 does not support security layers");
    }

    @Override
    public Object getNegotiatedProperty(String propName) {
        return null;
    }

    @Override
    public void dispose() throws SaslException {
        // Nothing to dispose
    }
}
