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
import request from 'supertest';
import { Attestation, CeremonyContext, SoftwareAuthenticator } from './webauthn-authenticator';

const cheerio = require('cheerio');

const MAX_REDIRECTS = 10;

/** The application a WebAuthn ceremony runs for, and the gateway serving its security domain. */
export interface WebAuthnClient {
  /** Gateway base URL, without the security domain path. */
  gatewayUrl: string;
  domainHrid: string;
  clientId: string;
  redirectUri: string;
  /** The WebAuthn origin of the security domain, reported by the browser in the client data. */
  origin: string;
  /**
   * The URL the browser reaches the gateway on, when a proxy or an entrypoint host sits in front of
   * `gatewayUrl`: every request carries it in X-Forwarded-Host and X-Forwarded-Proto, and the gateway
   * URLs built on it (redirections, form actions) are sent to `gatewayUrl`.
   */
  publicUrl?: string;
}

/** What a ceremony overrides in the browser and authenticator behaviour. The origin defaults to the client's. */
export type CeremonyOverrides = Partial<CeremonyContext>;

export interface CeremonyResult {
  /** Status of the form post that completes the ceremony. */
  status: number;
  /** Where that form post redirects to. */
  location: string;
  /** The `error` query parameter of that redirection, when the ceremony failed. */
  error?: string;
  /** The authorization code the flow ended with, once every redirection has been followed. */
  code?: string;
}

/** The cookies of one browser session, merged response after response. */
class CookieJar {
  private readonly cookies = new Map<string, string>();

  store(response: request.Response): void {
    for (const cookie of [].concat(response.headers['set-cookie'] ?? [])) {
      const [pair, ...attributes] = String(cookie).split(';');
      const separator = pair.indexOf('=');
      const name = pair.slice(0, separator).trim();
      const value = pair.slice(separator + 1).trim();
      const expired = attributes.some((attribute) => /^\s*max-age=0\s*$/i.test(attribute));
      if (expired || value === '') {
        this.cookies.delete(name);
      } else {
        this.cookies.set(name, value);
      }
    }
  }

  get header(): string {
    return [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ');
  }
}

/** A browser session on the gateway: follows redirections by hand and keeps its cookies. */
class BrowserSession {
  private readonly jar = new CookieJar();

  constructor(private readonly client: WebAuthnClient) {}

  private store(response: request.Response): request.Response {
    this.jar.store(response);
    return response;
  }

  /** A request as the browser sends it: its cookies, and the public URL it believes it talks to. */
  private request(method: 'get' | 'post', url: string): request.Test {
    const { gatewayUrl, publicUrl } = this.client;
    const publicOrigin = publicUrl && new URL(publicUrl);
    const target = publicOrigin && url.startsWith(publicOrigin.origin) ? gatewayUrl + url.slice(publicOrigin.origin.length) : url;
    const test = request(target)[method]('').set('Cookie', this.jar.header);
    return publicOrigin
      ? test.set('X-Forwarded-Host', publicOrigin.host).set('X-Forwarded-Proto', publicOrigin.protocol.replace(':', ''))
      : test;
  }

  async get(url: string): Promise<request.Response> {
    return this.store(await this.request('get', url).send());
  }

  async postJson(url: string, body: Record<string, unknown>): Promise<request.Response> {
    return this.store(await this.request('post', url).set('Content-Type', 'application/json').send(body));
  }

  async postForm(url: string, fields: Record<string, string>): Promise<request.Response> {
    return this.store(await this.request('post', url).type('form').send(fields));
  }

  /** Starts the authorization code flow; returns where the gateway sends the user to sign in. */
  async authorize(): Promise<string> {
    const { gatewayUrl, domainHrid, clientId, redirectUri } = this.client;
    const query = `response_type=code&client_id=${encodeURIComponent(clientId)}&redirect_uri=${encodeURIComponent(redirectUri)}`;
    const response = await this.get(`${gatewayUrl}/${domainHrid}/oauth/authorize?${query}`);
    return expectRedirect(response);
  }

  /**
   * Loads a gateway page and returns its form: the URL it posts to, and the fields it carries along,
   * i.e. the XSRF token when the page has one (the WebAuthn pages only have one with CSRF enabled on them).
   */
  async form(url: string): Promise<{ action: string; fields: Record<string, string> }> {
    const response = await this.get(url);
    if (response.status !== 200) {
      throw new Error(`GET ${url} returned ${response.status} (location: ${response.headers['location']})`);
    }
    const dom = cheerio.load(response.text);
    const token = dom('[name=X-XSRF-TOKEN]').val();
    const action = dom('form').attr('action');
    if (!action) {
      throw new Error(`No form on ${url}: ${response.text?.substring(0, 500)}`);
    }
    return { action: new URL(action, url).toString(), fields: token ? { 'X-XSRF-TOKEN': token } : {} };
  }

  /** Follows redirections from `location` until `stop` matches one, or the gateway stops redirecting. */
  async followUntil(location: string, stop: (location: string) => boolean): Promise<string> {
    for (let hop = 0; hop < MAX_REDIRECTS && !stop(location); hop++) {
      const response = await this.get(location);
      if (response.status !== 302) {
        return location;
      }
      location = response.headers['location'];
    }
    return location;
  }

  /** Reports a ceremony from the response to its final form post, following a success to the authorization code. */
  async result(response: request.Response): Promise<CeremonyResult> {
    const location = response.headers['location'];
    if (response.status !== 302 || !location) {
      return { status: response.status, location };
    }
    const error = new URL(location, this.client.gatewayUrl).searchParams.get('error') ?? undefined;
    if (error) {
      return { status: response.status, location, error };
    }
    const callback = await this.followUntil(location, (candidate) => candidate.startsWith(this.client.redirectUri));
    const code = callback.startsWith(this.client.redirectUri) ? new URL(callback).searchParams.get('code') ?? undefined : undefined;
    return { status: response.status, location, code };
  }

  context(overrides: CeremonyOverrides): CeremonyContext {
    return { origin: this.client.origin, ...overrides };
  }
}

function expectRedirect(response: request.Response): string {
  if (response.status !== 302) {
    throw new Error(`Expected a redirection from ${response.request?.url}, got ${response.status}: ${response.text?.substring(0, 500)}`);
  }
  return response.headers['location'];
}

/**
 * Signs in with a password, then registers a WebAuthn credential on the registration page the
 * gateway redirects to (passwordless must be enabled and the user must have no credential yet,
 * unless the security domain forces the registration).
 */
export async function registerWebAuthnCredential(
  client: WebAuthnClient,
  user: { username: string; password: string },
  authenticator: SoftwareAuthenticator,
  attestation: Attestation = { format: 'none' },
  overrides: CeremonyOverrides = {},
): Promise<CeremonyResult> {
  const session = new BrowserSession(client);
  const login = await session.form(await session.authorize());
  const loggedIn = await session.postForm(login.action, {
    ...login.fields,
    username: user.username,
    password: user.password,
    client_id: client.clientId,
  });

  const registerPage = await session.followUntil(expectRedirect(loggedIn), (location) =>
    new URL(location).pathname.endsWith('/webauthn/register'),
  );
  if (!new URL(registerPage).pathname.endsWith('/webauthn/register')) {
    throw new Error(`The gateway did not offer the WebAuthn registration after the password login, it ended on ${registerPage}`);
  }
  const register = await session.form(registerPage);

  // What webauthn-register-v4.js does: creation options first, then the credential through the form.
  const options = await session.postJson(register.action.replace('/webauthn/register', '/webauthn/register/credentials'), {
    name: user.username,
    displayName: user.username,
  });
  if (options.status !== 200) {
    throw new Error(`The gateway refused the WebAuthn creation options with ${options.status}: ${options.text}`);
  }
  const credential = authenticator.createCredential(options.body, session.context(overrides), attestation);

  return session.result(
    await session.postForm(register.action, {
      ...register.fields,
      username: user.username,
      assertion: JSON.stringify(credential),
    }),
  );
}

/**
 * Signs in without a password: opens the passwordless login page the login page links to, and
 * presents an assertion of `authenticator` for `username`.
 */
export async function signInWithWebAuthn(
  client: WebAuthnClient,
  username: string,
  authenticator: SoftwareAuthenticator,
  overrides: CeremonyOverrides = {},
): Promise<CeremonyResult> {
  const session = new BrowserSession(client);
  const loginPage = new URL(await session.authorize());
  loginPage.pathname = loginPage.pathname.replace(/\/login$/, '/webauthn/login');
  const login = await session.form(loginPage.toString());

  // What webauthn-login-v4.js does: request options first, then the assertion through the form.
  const options = await session.postJson(login.action.replace('/webauthn/login', '/webauthn/login/credentials'), { name: username });
  if (options.status !== 200) {
    throw new Error(`The gateway refused the WebAuthn request options with ${options.status}: ${options.text}`);
  }
  const assertion = authenticator.getAssertion(options.body, session.context(overrides));

  return session.result(
    await session.postForm(login.action, {
      ...login.fields,
      username,
      assertion: JSON.stringify(assertion),
    }),
  );
}
