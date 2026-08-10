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

import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { BasicResponse } from '@utils-commands/misc';

import { toReachableUrl } from './keycloak-realm';

/**
 * Minimal per-host cookie store.
 *
 * The flow crosses two servers that each maintain their own session — AM remembers the
 * pending authorize request, Keycloak remembers the authenticated user. A browser keeps
 * those apart; sharing one jar hands AM Keycloak's session and the login never completes.
 */
export class CookieJar {
  private readonly byHost = new Map<string, Map<string, string>>();

  private static host(url: string): string {
    return new URL(url).host;
  }

  store(url: string, setCookie?: string | string[]): void {
    if (!setCookie) {
      return;
    }
    const host = CookieJar.host(url);
    const jar = this.byHost.get(host) ?? new Map<string, string>();
    for (const raw of Array.isArray(setCookie) ? setCookie : [setCookie]) {
      const [pair] = raw.split(';');
      const index = pair.indexOf('=');
      if (index > 0) {
        jar.set(pair.slice(0, index).trim(), pair.slice(index + 1).trim());
      }
    }
    this.byHost.set(host, jar);
  }

  headerFor(url: string): Record<string, string> {
    const jar = this.byHost.get(CookieJar.host(url));
    if (!jar || jar.size === 0) {
      return {};
    }
    return { Cookie: [...jar.entries()].map(([name, value]) => `${name}=${value}`).join('; ') };
  }
}

/** GET that sends and records cookies for the target host only. */
export async function getWithJar(jar: CookieJar, url: string): Promise<BasicResponse> {
  const response = await performGet(url, '', jar.headerFor(url));
  jar.store(url, response.headers['set-cookie']);
  return response;
}

/**
 * Form POST that sends and records cookies for the target host only.
 *
 * The body is url-encoded by hand rather than using the shared form helper, which sends
 * multipart/form-data: Netty's multipart decoder caps attribute size and a signed SAML
 * assertion exceeds it, which the gateway reports as "Size exceed allowed maximum
 * capacity". Browsers post these forms url-encoded, which has no such limit.
 */
export async function postWithJar(jar: CookieJar, url: string, fields: Record<string, string>): Promise<BasicResponse> {
  const body = Object.entries(fields)
    .map(([name, value]) => `${encodeURIComponent(name)}=${encodeURIComponent(value)}`)
    .join('&');
  const response = await performPost(url, '', body, {
    ...jar.headerFor(url),
    'Content-type': 'application/x-www-form-urlencoded',
  });
  jar.store(url, response.headers['set-cookie']);
  return response;
}

/**
 * Post an auto-submitting form.
 *
 * The shared `followRedirectTag` helper compares content-type with strict equality
 * against 'text/html', which Keycloak's 'text/html;charset=utf-8' fails, so its forms
 * have to be submitted here instead.
 */
export async function submitAutoPostForm(
  jar: CookieJar,
  response: BasicResponse,
  tamperSamlResponse?: (xml: string) => string,
): Promise<BasicResponse> {
  const dom = cheerio.load(response.text ?? '');
  const form = dom('form').first();
  const action = form.attr('action');
  expect(action).toEqual(expect.any(String));

  const fields: Record<string, string> = {};
  form.find('input[type="hidden"]').each((_, input) => {
    const name = dom(input).attr('name');
    const value = dom(input).attr('value');
    if (name && value !== undefined) {
      fields[name] = value;
    }
  });

  // HTTP-POST binding carries the response as plain base64 (no DEFLATE), so it can be
  // decoded, rewritten and re-encoded without touching the transport.
  if (tamperSamlResponse && fields.SAMLResponse) {
    const decoded = Buffer.from(fields.SAMLResponse, 'base64').toString('utf8');
    fields.SAMLResponse = Buffer.from(tamperSamlResponse(decoded), 'utf8').toString('base64');
  }

  return postWithJar(jar, toReachableUrl(action!), fields);
}
