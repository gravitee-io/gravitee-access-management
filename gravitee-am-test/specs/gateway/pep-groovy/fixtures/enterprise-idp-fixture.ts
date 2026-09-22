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
import { readFileSync } from 'fs';
import { join } from 'path';
import request from 'supertest';
import jwt from 'jsonwebtoken';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  DomainOidcConfig,
  getDomainFlows,
  safeDeleteDomain,
  startDomain,
  updateDomainFlows,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { defaultIdpId } from '@management-commands/idp-management-commands';
import { getAllCertificates } from '@management-commands/certificate-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { FlowEntityTypeEnum } from '../../../../api/management/models';
import { createTrustedDomain } from '../../../management/domain/fixtures/cross-app-access-fixture';
import { Fixture } from '../../../test-fixture';

export const TOKEN_EXCHANGE_GRANT = 'urn:ietf:params:oauth:grant-type:token-exchange';
export const ID_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:id_token';
export const ID_JAG_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:id-jag';
export const ISSUE_ID_JAG = 'issue:id_jag:token_exchange';

export const AGENTS = ['claude', 'codex'] as const;
export const USERS = ['user1', 'user2'] as const;
export const PARTNERS = {
  chat: { name: 'Chat', audience: 'https://auth.chat.com', resource: 'https://chat.com' },
  weather: { name: 'Weather', audience: 'https://auth.weather.com', resource: 'https://weather.com' },
  calculator: { name: 'Calculator', audience: 'https://auth.calculator.com', resource: 'https://calculator.com' },
} as const;

export type Agent = typeof AGENTS[number];
export type EnterpriseUser = typeof USERS[number];
export type Partner = keyof typeof PARTNERS;

export interface Tuple {
  subject: Agent | EnterpriseUser;
  action: typeof ISSUE_ID_JAG;
  resource: string;
}

export interface IdJagRequest {
  agent: Agent;
  user: EnterpriseUser;
  partner: Partner;
}

export interface EnterpriseIdpFixture extends Fixture {
  domainId: string;
  oidc: DomainOidcConfig;
  signIn: (user: EnterpriseUser, agent: Agent) => Promise<string>;
  requestIdJag: (idJagRequest: IdJagRequest) => Promise<request.Response>;
  subjectOf: (label: Agent | EnterpriseUser) => string;
  setPep: (tuples: Tuple[] | null, options?: { inspect?: boolean }) => Promise<void>;
}

const USER_PASSWORD = 'SomeP@ssw0rd';
const REDIRECT_URI = 'https://gravitee.io/callback';
const PEP_POLICY_TEMPLATE = readFileSync(join(__dirname, 'pep-policy.groovy'), 'utf8');

const groovyString = (value: string) => `'${value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;

const renderPepPolicy = (tuples: { subject: string; action: string; resource: string }[], inspect: boolean) =>
  PEP_POLICY_TEMPLATE.replace('{{INSPECT}}', String(inspect)).replace(
    '{{PERMITS}}',
    '[\n' +
      tuples
        .map((t) => `    [subject: ${groovyString(t.subject)}, action: ${groovyString(t.action)}, resource: ${groovyString(t.resource)}]`)
        .join(',\n') +
      '\n]',
  );

const pepStep = (script: string) => ({
  name: 'AuthZ PEP',
  policy: 'groovy',
  description: 'Mimics an AuthZEN PEP: user and client must both be permitted to have an ID-JAG issued for the audience',
  condition: '',
  enabled: true,
  configuration: JSON.stringify({ onRequestScript: script }),
});

const subOf = (token: string): string => (jwt.decode(token) as { sub: string }).sub;

export const setupEnterpriseIdp = async (): Promise<EnterpriseIdpFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('enterprise-idp', true), 'Enterprise IdP issuing ID-JAGs to agents');

  try {
    await request(getDomainManagerUrl(domain.id))
      .patch('')
      .set('Authorization', `Bearer ${accessToken}`)
      .set('Content-Type', 'application/json')
      .send({
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: [ID_TOKEN_TYPE],
          allowedRequestedTokenTypes: [ID_JAG_TOKEN_TYPE],
        },
      })
      .expect(200);

    const partnerResourceServers = await Promise.all(
      Object.values(PARTNERS).map(async (partner) => {
        const trustDomain = await createTrustedDomain(domain.id, accessToken, {
          name: partner.name,
          domainIdentifier: partner.audience,
          crossAppAccess: {
            enabled: true,
            resourceServers: [{ name: partner.name, resource: partner.resource }],
          },
        });
        return { partner, trustDomainId: trustDomain.id, resourceServerId: trustDomain.crossAppAccess.resourceServers[0].id };
      }),
    );

    const certificate = (await getAllCertificates(domain.id, accessToken))[0].id;

    const agentSecrets = new Map<Agent, string>();
    for (const agent of AGENTS) {
      const created = await createApplication(domain.id, accessToken, {
        name: agent,
        type: 'WEB',
        clientId: agent,
        redirectUris: [REDIRECT_URI],
      });
      await updateApplication(
        domain.id,
        accessToken,
        {
          certificate,
          settings: {
            oauth: {
              redirectUris: [REDIRECT_URI],
              grantTypes: ['password', TOKEN_EXCHANGE_GRANT],
              scopeSettings: [{ scope: 'openid', defaultScope: false }],
              idJagValiditySeconds: 300,
              crossAppAccessSettings: {
                enabled: true,
                resourceServers: partnerResourceServers.map(({ partner, trustDomainId, resourceServerId }) => ({
                  trustDomainId,
                  resourceServerId,
                  clientId: `${agent}-at-${partner.name.toLowerCase()}`,
                })),
              },
            },
          },
          identityProviders: new Set([{ identity: defaultIdpId(domain.id), priority: 0 }]),
        } as any,
        created.id,
      );
      agentSecrets.set(agent, created.settings.oauth.clientSecret);
    }

    for (const [index, username] of USERS.entries()) {
      await createUser(domain.id, accessToken, {
        firstName: 'Enterprise',
        lastName: `User${index + 1}`,
        email: `${username}@enterprise.example.com`,
        username,
        password: USER_PASSWORD,
        preRegistration: false,
      });
    }

    const started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
    const oidc = started.oidcConfig;

    const basicAuth = (agent: Agent) => 'Basic ' + getBase64BasicAuth(agent, agentSecrets.get(agent));

    const tokenRequest = (agent: Agent, body: string) =>
      performPost(oidc.token_endpoint, '', body, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: basicAuth(agent),
      });

    const signIn = async (user: EnterpriseUser, agent: Agent): Promise<string> => {
      const response = await tokenRequest(
        agent,
        `grant_type=password&username=${user}&password=${encodeURIComponent(USER_PASSWORD)}&scope=openid`,
      ).expect(200);
      return response.body.id_token;
    };

    const requestIdJag = async ({ agent, user, partner }: IdJagRequest): Promise<request.Response> => {
      const idToken = await signIn(user, agent);
      const params = new URLSearchParams({
        grant_type: TOKEN_EXCHANGE_GRANT,
        subject_token: idToken,
        subject_token_type: ID_TOKEN_TYPE,
        requested_token_type: ID_JAG_TOKEN_TYPE,
        audience: PARTNERS[partner].audience,
        resource: PARTNERS[partner].resource,
      });
      return tokenRequest(agent, params.toString());
    };

    const subjects = new Map<Agent | EnterpriseUser, string>(AGENTS.map((agent) => [agent, agent]));
    for (const user of USERS) {
      const perAgent = await Promise.all(AGENTS.map(async (agent) => subOf(await signIn(user, agent))));
      if (new Set(perAgent).size !== 1) {
        throw new Error(`${user} has a different sub per agent: ${perAgent.join(', ')}`);
      }
      subjects.set(user, perAgent[0]);
    }
    const subjectOf = (label: Agent | EnterpriseUser) => subjects.get(label);

    const setPep = async (tuples: Tuple[] | null, { inspect = false }: { inspect?: boolean } = {}) => {
      const flows = await getDomainFlows(domain.id, accessToken);
      const steps = tuples
        ? [
            pepStep(
              renderPepPolicy(
                tuples.map((t) => ({ ...t, subject: subjectOf(t.subject) })),
                inspect,
              ),
            ),
          ]
        : [];
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Token, 'pre', steps);
      await waitForSyncAfter(domain.id, () => updateDomainFlows(domain.id, accessToken, flows));
    };

    return {
      accessToken,
      domainId: domain.id,
      oidc,
      signIn,
      requestIdJag,
      subjectOf,
      setPep,
      cleanUp: () => safeDeleteDomain(domain.id, accessToken),
    };
  } catch (error) {
    await safeDeleteDomain(domain.id, accessToken);
    throw error;
  }
};
