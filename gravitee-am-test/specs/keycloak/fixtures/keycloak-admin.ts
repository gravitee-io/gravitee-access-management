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
import { KEYCLOAK_EXTERNAL } from './keycloak-realm';

const ADMIN_USERNAME = process.env.KEYCLOAK_ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin';

const adminAuthorization = async (): Promise<string> => {
  const response = await request(KEYCLOAK_EXTERNAL)
    .post('/realms/master/protocol/openid-connect/token')
    .type('form')
    .send({ grant_type: 'password', client_id: 'admin-cli', username: ADMIN_USERNAME, password: ADMIN_PASSWORD })
    .expect(200);
  return `Bearer ${response.body.access_token}`;
};

const adminPost = async (path: string, body: Record<string, unknown>, status: number) =>
  request(KEYCLOAK_EXTERNAL)
    .post(`/admin/realms${path}`)
    .set('Authorization', await adminAuthorization())
    .send(body)
    .expect(status);

export const createRealm = async (realm: string): Promise<void> => {
  await adminPost('', { realm, enabled: true, sslRequired: 'none' }, 201);
};

export const deleteRealm = async (realm: string): Promise<void> => {
  await request(KEYCLOAK_EXTERNAL)
    .delete(`/admin/realms/${realm}`)
    .set('Authorization', await adminAuthorization());
};

export const createIdentityProvider = async (realm: string, representation: Record<string, unknown>): Promise<void> => {
  await adminPost(`/${realm}/identity-provider/instances`, representation, 201);
};

export const createClient = async (realm: string, representation: Record<string, unknown>): Promise<void> => {
  await adminPost(`/${realm}/clients`, representation, 201);
};

export const createUser = async (realm: string, representation: Record<string, unknown>): Promise<string> => {
  const response = await adminPost(`/${realm}/users`, representation, 201);
  return response.headers['location'].split('/').pop();
};

export const linkFederatedIdentity = async (
  realm: string,
  userId: string,
  alias: string,
  federatedUserId: string,
  federatedUsername: string,
): Promise<void> => {
  await adminPost(
    `/${realm}/users/${userId}/federated-identity/${alias}`,
    { identityProvider: alias, userId: federatedUserId, userName: federatedUsername },
    204,
  );
};
