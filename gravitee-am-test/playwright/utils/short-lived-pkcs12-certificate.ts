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

import { execFileSync } from 'child_process';
import { mkdtempSync, readFileSync, rmSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';

import type { NewCertificate } from '@management-models/NewCertificate';

const PKCS12_PASSWORD = 'changeme';
const CERT_ALIAS = 'test';

/**
 * Build a {@link NewCertificate} for pkcs12-am-certificate expiring after {@param validDays} days.
 * Uses OpenSSL (same as typical CI/Linux dev images) so the keystore is accepted by AM.
 */
export function buildShortLivedPkcs12NewCertificate(displayName: string, validDays: number): NewCertificate {
  const dir = mkdtempSync(join(tmpdir(), 'am-pw-cert-'));
  try {
    const keyPem = join(dir, 'key.pem');
    const certPem = join(dir, 'cert.pem');
    const p12Path = join(dir, 'out.p12');

    execFileSync(
      'openssl',
      [
        'req',
        '-x509',
        '-newkey',
        'rsa:2048',
        '-keyout',
        keyPem,
        '-out',
        certPem,
        '-days',
        String(validDays),
        '-nodes',
        '-subj',
        '/CN=am-playwright-near-expiry.test',
      ],
      { stdio: 'pipe' },
    );
    execFileSync(
      'openssl',
      [
        'pkcs12',
        '-export',
        '-out',
        p12Path,
        '-inkey',
        keyPem,
        '-in',
        certPem,
        '-name',
        CERT_ALIAS,
        '-password',
        `pass:${PKCS12_PASSWORD}`,
      ],
      { stdio: 'pipe' },
    );

    const contentB64 = readFileSync(p12Path).toString('base64');
    const fileMeta = JSON.stringify({
      name: `${displayName}.p12`,
      type: 'application/x-pkcs12',
      size: new TextEncoder().encode(contentB64).length,
      content: contentB64,
    });

    return {
      name: displayName,
      type: 'pkcs12-am-certificate',
      configuration: JSON.stringify({
        storepass: PKCS12_PASSWORD,
        alias: CERT_ALIAS,
        keypass: PKCS12_PASSWORD,
        algorithm: 'RS256',
        use: ['sig', 'enc'],
        content: fileMeta,
      }),
    };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}
