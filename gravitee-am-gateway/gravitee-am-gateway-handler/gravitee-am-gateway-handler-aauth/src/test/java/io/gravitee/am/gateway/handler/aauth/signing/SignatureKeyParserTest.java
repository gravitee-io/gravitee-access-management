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
package io.gravitee.am.gateway.handler.aauth.signing;

import org.junit.Test;

import static org.junit.Assert.*;

public class SignatureKeyParserTest {

    @Test
    public void shouldParseHwkOkpEd25519() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("sig=hwk;kty=\"OKP\";crv=\"Ed25519\";x=\"abc123\"");

        assertEquals("sig", info.label());
        assertEquals("hwk", info.scheme());
        assertEquals("OKP", info.getParam("kty"));
        assertEquals("Ed25519", info.getParam("crv"));
        assertEquals("abc123", info.getParam("x"));
    }

    @Test
    public void shouldParseHwkEcP256() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("sig=hwk;kty=\"EC\";crv=\"P-256\";x=\"xval\";y=\"yval\"");

        assertEquals("sig", info.label());
        assertEquals("hwk", info.scheme());
        assertEquals("EC", info.getParam("kty"));
        assertEquals("P-256", info.getParam("crv"));
        assertEquals("xval", info.getParam("x"));
        assertEquals("yval", info.getParam("y"));
    }

    @Test
    public void shouldParseJwtScheme() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("sig=jwt; jwt=\"eyJhbGciOiJFZERTQSJ9.payload.sig\"");

        assertEquals("sig", info.label());
        assertEquals("jwt", info.scheme());
        assertEquals("eyJhbGciOiJFZERTQSJ9.payload.sig", info.getParam("jwt"));
    }

    @Test
    public void shouldParseSchemeWithoutParams() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("sig=hwk");

        assertEquals("sig", info.label());
        assertEquals("hwk", info.scheme());
        assertTrue(info.params().isEmpty());
    }

    @Test
    public void shouldParseCustomLabel() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("mysig=hwk;kty=\"OKP\"");

        assertEquals("mysig", info.label());
        assertEquals("hwk", info.scheme());
        assertEquals("OKP", info.getParam("kty"));
    }

    @Test
    public void shouldHandleWhitespaceInParams() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("sig=hwk; kty=\"OKP\"; crv=\"Ed25519\"");

        assertEquals("OKP", info.getParam("kty"));
        assertEquals("Ed25519", info.getParam("crv"));
    }

    @Test
    public void shouldHandleUnquotedParamValue() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("sig=hwk;kty=OKP;crv=Ed25519");

        assertEquals("OKP", info.getParam("kty"));
        assertEquals("Ed25519", info.getParam("crv"));
    }

    @Test
    public void shouldReturnNullForMissingParam() throws Exception {
        SignatureKeyInfo info = SignatureKeyParser.parse("sig=hwk;kty=\"OKP\"");

        assertNull(info.getParam("crv"));
        assertNull(info.getParam("nonexistent"));
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectNullHeader() throws Exception {
        SignatureKeyParser.parse(null);
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectEmptyHeader() throws Exception {
        SignatureKeyParser.parse("");
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectBlankHeader() throws Exception {
        SignatureKeyParser.parse("   ");
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectMalformedHeader() throws Exception {
        SignatureKeyParser.parse("no-equals-sign");
    }

    @Test(expected = SignatureVerificationException.class)
    public void shouldRejectUnclosedQuote() throws Exception {
        SignatureKeyParser.parse("sig=hwk;kty=\"OKP");
    }
}
