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
import { cookieHeaderFromSetCookie } from './management-auth-helper';

/**
 * Parse a Location header (absolute URL or path) into origin and path+query.
 * Relative locations are resolved against baseOrigin.
 */
export function parseLocation(
  location: string,
  baseOrigin: string,
): { origin: string; pathAndSearch: string } {
  if (!location) throw new Error('Empty location');
  if (location.startsWith('http://') || location.startsWith('https://')) {
    const u = new URL(location);
    return { origin: u.origin, pathAndSearch: u.pathname + u.search };
  }
  const base = baseOrigin.endsWith('/') ? baseOrigin : baseOrigin + '/';
  const u = new URL(location, base);
  return { origin: u.origin, pathAndSearch: u.pathname + u.search };
}

/**
 * Start management auth flow: GET authorize, follow redirect to login page, return login form response and cookie.
 * Fails if location or Set-Cookie is missing (deterministic true path).
 */
export async function getLoginForm(managementUrl: string, redirectUri: string) {
  const authorizePath = `/management/auth/authorize?redirect_uri=${encodeURIComponent(redirectUri)}`;
  const initiateRes = await performGet(managementUrl, authorizePath);
  const location = initiateRes.headers.location;
  expect(location).toBeDefined();
  const { origin, pathAndSearch } = parseLocation(location!, managementUrl);
  const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
  expect(cookie).toBeDefined();
  const loginFormRes = await performGet(origin, pathAndSearch, { Cookie: cookie! });
  return { loginFormRes, cookie: cookie! };
}
