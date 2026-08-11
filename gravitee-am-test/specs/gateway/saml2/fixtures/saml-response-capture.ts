/*
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
import { expect } from '@jest/globals';
import cheerio from 'cheerio';
import * as zlib from 'zlib';

import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { BasicResponse, followRedirectTag } from '@utils-commands/misc';

import { SamlTestDomains } from '../setup';

/**
 * Captures the SAML response a login produces, so tests can assert on the assertion
 * itself rather than only on its side effects.
 *
 * The login helper in `../setup` follows the whole redirect chain and returns only the
 * final hop, which discards the response. Rather than modify that shared helper, this
 * walks the same chain and stops at the redirect carrying `SAMLResponse`.
 */

/** Maximum redirects to walk before giving up looking for the SAML response. */
const MAX_REDIRECTS = 6;

/**
 * Decode a `SAMLResponse` parameter into XML.
 *
 * HTTP-Redirect binding DEFLATEs the payload before base64; HTTP-POST binding does not.
 * Try raw inflate first and fall back to treating the bytes as plain XML.
 */
export function decodeSamlResponse(encoded: string): string {
  const raw = Buffer.from(decodeURIComponent(encoded), 'base64');
  try {
    return zlib.inflateRawSync(raw).toString('utf8');
  } catch {
    return raw.toString('utf8');
  }
}

/** Pull the `SAMLResponse` parameter out of a redirect Location, if present. */
function samlResponseFromLocation(location?: string): string | undefined {
  if (!location || !location.includes('SAMLResponse=')) {
    return undefined;
  }
  return new URL(location).searchParams.get('SAMLResponse') ?? undefined;
}

/** Pull the `SAMLResponse` out of an auto-submitting HTML form (HTTP-POST binding). */
function samlResponseFromHtmlForm(body?: string): string | undefined {
  if (!body || !body.includes('SAMLResponse')) {
    return undefined;
  }
  const dom = cheerio.load(body);
  return dom('input[name="SAMLResponse"]').attr('value');
}

/** Locate the SAML provider button on the client domain's login page. */
function findSamlProviderLink(html: string): string | undefined {
  const dom = cheerio.load(html);
  return dom('.btn-saml2-generic-am-idp').attr('href') || dom('a[href*="saml2"]').attr('href');
}

/**
 * Run the SAML login flow and return the decoded SAML response XML.
 *
 * Walks the same steps as the shared login helper, but inspects each redirect for the
 * `SAMLResponse` parameter (HTTP-Redirect binding) or an auto-submit form field
 * (HTTP-POST binding) instead of following blindly to the end.
 */
export async function captureSamlResponseXml(
  domains: SamlTestDomains,
  clientOpenIdConfiguration: any,
  username: string,
  password: string,
): Promise<string> {
  const authorizeResponse = await initiateLoginFlow(
    domains.clientApplication.settings.oauth.clientId,
    clientOpenIdConfiguration,
    domains.clientDomain,
  );
  const cookies = authorizeResponse.headers['set-cookie'];
  const headers = cookies ? { Cookie: cookies } : {};

  const loginPage = await performGet(authorizeResponse.headers['location'], '', headers).expect(200);
  const samlProviderLink = findSamlProviderLink(loginPage.text);
  expect(samlProviderLink).toEqual(expect.any(String));

  let toIdp = await performGet(samlProviderLink!, '', headers);
  // Under HTTP-Redirect the SP 302s to the IdP; under HTTP-POST it returns an
  // auto-submitting form carrying the AuthnRequest, which has to be posted first.
  if (toIdp.status === 200 && (toIdp.text ?? '').includes('SAMLRequest')) {
    toIdp = await followRedirectTag('saml-authn-request')(toIdp);
  }

  let current: BasicResponse = await login(toIdp, username, domains.providerApplication.settings.oauth.clientId, password);

  for (let hop = 0; hop < MAX_REDIRECTS; hop++) {
    const fromLocation = samlResponseFromLocation(current.headers?.location);
    if (fromLocation) {
      return decodeSamlResponse(fromLocation);
    }
    const fromForm = samlResponseFromHtmlForm(current.text);
    if (fromForm) {
      return decodeSamlResponse(fromForm);
    }

    const nextLocation = current.headers?.location;
    if (nextLocation) {
      const hopCookies = current.headers['set-cookie'] ?? cookies;
      current = await performGet(nextLocation, '', hopCookies ? { Cookie: hopCookies } : {});
      continue;
    }

    // Under HTTP-POST the IdP re-presents the AuthnRequest as an auto-submitting form
    // after authentication ("Login SSO POST"), which must be posted to continue.
    if (current.status === 200 && (current.text ?? '').includes('<form')) {
      current = await followRedirectTag('saml-form-hop')(current);
      continue;
    }

    break;
  }

  throw new Error(`No SAMLResponse found within ${MAX_REDIRECTS} redirects of the login flow`);
}

/** True when the response carries an encrypted assertion rather than a plaintext one. */
export function hasEncryptedAssertion(xml: string): boolean {
  return xml.includes('EncryptedAssertion');
}

/** True when the response carries a readable (unencrypted) assertion. */
export function hasPlaintextAssertion(xml: string): boolean {
  return /<(saml2?:)?Assertion[ >]/.test(xml);
}

/**
 * XML Encryption algorithms in use: the data (block) algorithm from `EncryptedData`
 * and the key transport algorithm from `EncryptedKey`.
 */
export function encryptionAlgorithms(xml: string): { data?: string; keyTransport?: string } {
  const data = /<xenc:EncryptedData[\s\S]*?<xenc:EncryptionMethod Algorithm="([^"]+)"/.exec(xml);
  const keyTransport = /<xenc:EncryptedKey[\s\S]*?<xenc:EncryptionMethod Algorithm="([^"]+)"/.exec(xml);
  return { data: data?.[1], keyTransport: keyTransport?.[1] };
}

/**
 * Where signatures appear: directly on the Response element, and/or inside the
 * Assertion. An encrypted assertion hides any assertion-level signature, so only the
 * response-level one is observable in that case.
 */
export function signatureLevels(xml: string): { response: boolean; assertion: boolean } {
  const assertionStart = xml.search(/<(saml2?:)?Assertion[ >]/);
  const firstSignature = xml.indexOf('<ds:Signature');
  if (firstSignature === -1) {
    return { response: false, assertion: false };
  }
  if (assertionStart === -1) {
    return { response: true, assertion: false };
  }
  return {
    response: firstSignature < assertionStart,
    assertion: xml.slice(assertionStart).includes('<ds:Signature'),
  };
}

/**
 * Start the SAML flow and return the response at the point it stops progressing,
 * without asserting success.
 *
 * Some misconfigurations (an SP entity ID the IdP cannot resolve, for example) fail
 * while the AuthnRequest is being handled — before any login form is shown — so the
 * error never reaches a test that drives the full credential flow.
 */
export async function initiateSamlFlow(
  domains: SamlTestDomains,
  clientOpenIdConfiguration: any,
): Promise<{ response: BasicResponse; locations: string[] }> {
  const authorizeResponse = await initiateLoginFlow(
    domains.clientApplication.settings.oauth.clientId,
    clientOpenIdConfiguration,
    domains.clientDomain,
  );
  const cookies = authorizeResponse.headers['set-cookie'];
  const headers = cookies ? { Cookie: cookies } : {};

  const loginPage = await performGet(authorizeResponse.headers['location'], '', headers).expect(200);
  const samlProviderLink = findSamlProviderLink(loginPage.text);
  expect(samlProviderLink).toEqual(expect.any(String));

  let current: BasicResponse = await performGet(samlProviderLink!, '', headers);
  // Errors are frequently carried in the redirect URL rather than the final page body,
  // so every hop's Location is returned alongside the response that ends the chain.
  const locations: string[] = [];

  for (let hop = 0; hop < MAX_REDIRECTS; hop++) {
    const nextLocation = current.headers?.location;
    if (!nextLocation) {
      return { response: current, locations };
    }
    locations.push(nextLocation);
    const hopCookies = current.headers['set-cookie'] ?? cookies;
    current = await performGet(nextLocation, '', hopCookies ? { Cookie: hopCookies } : {});
  }
  return { response: current, locations };
}
