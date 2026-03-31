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
import { performGet } from '@gateway-commands/oauth-oidc-commands';

const cheerio = require('cheerio');

export function extractDomValue(response, selector): string {
  const dom = cheerio.load(response.text);
  const value = dom(selector).val();
  expect(value).toBeDefined();
  return value;
}

export function extractDomAttr(response, selector, attr): string {
  const dom = cheerio.load(response.text);
  const value = dom(selector).attr(attr);
  expect(value).toBeDefined();
  return value;
}

export function extractCookieSessionValues(cookie): any {
  const jwt = cookie[0]
    .split('; ')
    .filter((str) => str.includes('GRAVITEE_IO_AM_SESSION'))[0]
    .split('=')[1]
    .split('.')[1];
  const decoded = Buffer.from(jwt, 'base64').toString('binary');
  return JSON.parse(decoded);
}

export async function extractSmsCode(sfrUrl): Promise<string> {
  const requests = await performGet(sfrUrl, '/__admin/requests?limit=1');
  expect(requests.status).toBe(200);
  const body = decodeURIComponent(requests.body.requests[0].request.body);
  const params = new URLSearchParams(body);
  const messageUnitaireRaw = params.get('messageUnitaire');
  const messageUnitaireJson = JSON.parse(decodeURIComponent(messageUnitaireRaw));
  const code = messageUnitaireJson.msgContent;

  expect(code).toBeDefined();
  return code;
}

/** Parses the MFA enrollment page: XSRF token, form action, and embedded factors JSON. */
export function parseEnrollPage(result): { factors: any[]; action: string; token: string } {
  const dom = cheerio.load(result.text);
  const token = dom('[name=X-XSRF-TOKEN]').val();
  const action = dom('form').attr('action');
  expect(token).toBeDefined();
  expect(action).toBeDefined();
  const codeString = dom('script')
    .text()
    .split('\n')
    .find((line: string) => line.trim().startsWith('const factors'));
  const match = codeString.match(/\[.*]/);
  if (!match) throw new Error('Cannot extract factors');
  return { factors: JSON.parse(match[0]), action, token };
}

export function extractSharedSecret(result, factorType: string = 'SMS'): any {
  const { factors, action, token } = parseEnrollPage(result);
  const factor = factors.find((f) => f.factorType === factorType);
  return { sharedSecret: factor?.enrollment?.key || null, action, token };
}

/** Resolves enrollment entry by factor id first, then falls back to factorType. */
export function extractSharedSecretForFactor(result, managementFactor: { id: string }, factorTypeFallback: string = 'TOTP'): any {
  const { factors, action, token } = parseEnrollPage(result);
  const entry =
    factors.find((f: { id?: string }) => f.id === managementFactor.id) ||
    factors.find((f: { factorType?: string }) => f.factorType === factorTypeFallback);
  const sharedSecret = entry?.enrollment?.key ?? null;
  if (sharedSecret == null) {
    throw new Error(`Cannot extract enrollment shared secret for factor ${managementFactor.id}`);
  }
  return { sharedSecret, action, token };
}
