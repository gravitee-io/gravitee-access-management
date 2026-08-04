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

import { retryUntil } from '@utils-commands/retry';
import request from 'supertest';

/**
 * Drives the cockpit mock (gravitee-am-cockpit-mock) control API so cloud specs can push Cockpit
 * commands at the management API over its WebSocket connector. Only available when the stack is
 * brought up in managed-cloud mode (local-stack.sh --cloud / stack:ci:setup:cloud:*).
 */

const baseUrl = process.env.AM_COCKPIT_MOCK_URL;

export interface CockpitCommand {
  type: string;
  payload: Record<string, any>;
}

/** A message AM emitted, as surfaced on the cockpit mock's FIFO queue. */
export interface CockpitQueueEntry {
  protocolType: 'COMMAND' | 'REPLY' | 'UNKNOWN';
  type?: string;
  commandId?: string;
  commandStatus?: string;
  errorDetails?: string;
}

/** POST a command toward AM. Returns the generated command id; AM's reply lands on the mock queue. */
export const sendCockpitCommand = async (command: CockpitCommand): Promise<string> => {
  const response = await fetch(`${baseUrl}/_control/send`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(command),
  });
  if (response.status !== 200) {
    throw new Error(`cockpit mock /_control/send returned ${response.status}: ${await response.text()}`);
  }
  return (await response.json()).id;
};

/** Wait for AM's REPLY to the command identified by `commandId` and return it. */
export const waitForCockpitReply = (
  commandId: string,
  options?: { timeoutMillis?: number; intervalMillis?: number },
): Promise<CockpitQueueEntry> =>
  retryUntil(
    async (): Promise<CockpitQueueEntry | null> => {
      const response = await fetch(`${baseUrl}/_control/queue`);
      if (response.status === 204) {
        return null;
      }
      if (response.status !== 200) {
        throw new Error(`cockpit mock /_control/queue returned ${response.status}: ${await response.text()}`);
      }
      return (await response.json()) as CockpitQueueEntry;
    },
    (entry) => entry !== null && entry.protocolType === 'REPLY' && entry.commandId === commandId,
    {
      timeoutMillis: options?.timeoutMillis ?? 15000,
      intervalMillis: options?.intervalMillis ?? 500,
    },
  ) as Promise<CockpitQueueEntry>;

/** Identifies the user Cockpit is signing in, exactly as its own SSO token does. */
export interface CockpitSsoRequest {
  /** Cockpit user id. Must equal the `id` of the USER command that created the user in AM. */
  sub: string;
  organizationId: string;
  /**
   * Required in practice: AM resolves it through environmentService.findById, which errors when the
   * environment does not exist, and CockpitAuthenticationFilter answers 403 on any exception.
   */
  environmentId: string;
  /** Seconds until the token expires. Cockpit uses 10; raise it only to debug. */
  ttlSeconds?: number;
  /**
   * Absolute console URL to land on after sign-in. Real Cockpit omits this (AM issues a relative
   * redirect that works when UI and API share a host); the local stack splits them across ports, so
   * Playwright / Postman must supply it.
   */
  redirectUri?: string;
}

/**
 * Ask the mock to mint the short-lived RS512 token Cockpit would put in its redirect to AM.
 * Signed with the private key mounted on the mock, verified by AM against the certificate in the
 * keystore mounted on the management API.
 */
export const mintCockpitSsoToken = async (sso: CockpitSsoRequest): Promise<string> => {
  const response = await fetch(`${baseUrl}/_control/sso-token`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      sub: sso.sub,
      org: sso.organizationId,
      env: sso.environmentId,
      ttlSeconds: sso.ttlSeconds,
      ...(sso.redirectUri !== undefined ? { redirectUri: sso.redirectUri } : {}),
    }),
  });
  if (response.status !== 200) {
    throw new Error(`cockpit mock /_control/sso-token returned ${response.status}: ${await response.text()}`);
  }
  return (await response.json()).token;
};

/**
 * Sign in the way a real cloud user does: exchange a Cockpit-signed token at AM's /auth/cockpit
 * endpoint and keep the session cookie it sets. The cookie value is literally `Bearer <management JWT>`
 * — the same token shape `/auth/token` returns — so stripping the prefix yields a bearer token usable
 * for the rest of the management API. Its lifetime is `jwt.expire-after` (7 days by default), not the
 * 10 seconds of the Cockpit token that bought it.
 *
 * The user must already exist in the organization: AM looks it up by the token's `sub` (as external id)
 * plus source `cockpit`, so the USER command has to have been acknowledged first.
 */
export const cockpitSignIn = async (sso: CockpitSsoRequest): Promise<string> => {
  const token = await mintCockpitSsoToken(sso);

  const response = await request(process.env.AM_MANAGEMENT_URL)
    .get(`/management/auth/cockpit?token=${encodeURIComponent(token)}`)
    .redirects(0);

  if (response.status !== 302) {
    throw new Error(`AM /auth/cockpit returned ${response.status} for organization ${sso.organizationId}, expected a redirect`);
  }

  const rawCookies = response.headers['set-cookie'];
  const cookies: string[] = Array.isArray(rawCookies) ? rawCookies : rawCookies ? [rawCookies] : [];
  const authCookie = cookies.find((cookie) => cookie.startsWith('Auth-Graviteeio-AM='));
  if (!authCookie) {
    throw new Error(`AM /auth/cockpit set no Auth-Graviteeio-AM cookie for organization ${sso.organizationId}`);
  }

  // AM sets it as an RFC 2109 cookie (`Version=1`), so the value arrives wrapped in double quotes:
  // Auth-Graviteeio-AM="Bearer eyJ...". Unwrap before looking for the scheme, or the whole quoted
  // string is returned and every later call comes back 401.
  const raw = decodeURIComponent(authCookie.split(';')[0].substring('Auth-Graviteeio-AM='.length));
  const value = raw.startsWith('"') && raw.endsWith('"') ? raw.slice(1, -1) : raw;

  if (!value.startsWith('Bearer ')) {
    throw new Error(`AM /auth/cockpit set an Auth-Graviteeio-AM cookie without a Bearer prefix: ${value.substring(0, 24)}...`);
  }
  return value.substring('Bearer '.length);
};

/** Whether an AM instance is currently connected to the cockpit mock. */
export const isCockpitConnected = async (): Promise<boolean> => {
  const response = await fetch(`${baseUrl}/_control/status`);
  return response.status === 200 && (await response.json()).connected === true;
};

/** Wait until AM has established its WebSocket connection to the cockpit mock. */
export const waitForCockpitConnection = (options?: { timeoutMillis?: number; intervalMillis?: number }): Promise<boolean> =>
  retryUntil(
    () => isCockpitConnected().catch(() => false),
    (connected) => connected === true,
    {
      timeoutMillis: options?.timeoutMillis ?? 30000,
      intervalMillis: options?.intervalMillis ?? 1000,
    },
  );

/** Drain all pending entries from the mock queue so tests start with a clean slate. */
export const drainCockpitQueue = async (): Promise<void> => {
  for (;;) {
    const response = await fetch(`${baseUrl}/_control/queue`);
    if (response.status === 204) return;
    if (response.status !== 200) {
      throw new Error(`cockpit mock /_control/queue returned ${response.status}: ${await response.text()}`);
    }
  }
};

/**
 * Send a Cockpit ORGANIZATION command for the given org.
 * Omit `license` to clear the org license; supply a base64-encoded key to set it.
 */
export const sendOrgCommand = (orgId: string, license?: string): Promise<string> =>
  sendCockpitCommand({
    type: 'ORGANIZATION',
    payload: {
      id: orgId,
      name: orgId,
      hrids: [orgId.toLowerCase()],
      ...(license !== undefined ? { license } : {}),
    },
  });
