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

/**
 * Client for the data plane provisioning endpoints on the management node technical API
 * ({@code :18093/_node/dataplanes}). Hand written rather than generated: the technical API is not
 * described by the management OpenAPI spec, so there is no SDK for it.
 */

const basePath = process.env.AM_MANAGEMENT_NODE_URL;
const username = process.env.AM_ADMIN_USERNAME;
const password = process.env.AM_ADMIN_PASSWORD;

export type DataPlaneSummary = {
  id: string;
  name: string;
  type: string;
  gatewayUrl?: string;
  organizationId: string;
  environmentId: string;
  database?: string;
  hosts: string[];
  createdAt: number;
  updatedAt: number;
};

export type NewDataPlane = {
  id?: string;
  name?: string;
  type?: string;
  gatewayUrl?: string;
  organizationId?: string;
  environmentId?: string;
  configuration?: unknown;
};

/** Response plus its parsed body, so specs can assert on status and payload together. */
export type DataPlaneResponse<T> = {
  status: number;
  body: T;
  raw: string;
};

function authHeader(): Record<string, string> {
  return { Authorization: 'Basic ' + btoa(`${username}:${password}`) };
}

async function read<T>(response: Response): Promise<DataPlaneResponse<T>> {
  const raw = await response.text();
  let body: T;
  try {
    body = raw.length ? JSON.parse(raw) : (null as T);
  } catch {
    body = raw as unknown as T;
  }
  return { status: response.status, body, raw };
}

export const createDataPlane = async (payload: NewDataPlane | string): Promise<DataPlaneResponse<any>> => {
  const response = await fetch(`${basePath}/dataplanes`, {
    method: 'POST',
    headers: { ...authHeader(), 'Content-Type': 'application/json' },
    body: typeof payload === 'string' ? payload : JSON.stringify(payload),
  });
  return read(response);
};

export const listDataPlanes = async (): Promise<DataPlaneResponse<DataPlaneSummary[]>> => {
  const response = await fetch(`${basePath}/dataplanes`, { method: 'GET', headers: authHeader() });
  return read(response);
};

export const getDataPlane = async (id: string): Promise<DataPlaneResponse<DataPlaneSummary>> => {
  const response = await fetch(`${basePath}/dataplanes/${id}`, { method: 'GET', headers: authHeader() });
  return read(response);
};

export const deleteDataPlane = async (id: string): Promise<DataPlaneResponse<void>> => {
  const response = await fetch(`${basePath}/dataplanes/${id}`, { method: 'DELETE', headers: authHeader() });
  return read(response);
};

/** Same requests without credentials, to prove the technical API stays behind basic auth. */
export const unauthenticated = {
  list: () => fetch(`${basePath}/dataplanes`, { method: 'GET' }),
  get: (id: string) => fetch(`${basePath}/dataplanes/${id}`, { method: 'GET' }),
  create: (payload: NewDataPlane) =>
    fetch(`${basePath}/dataplanes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  delete: (id: string) => fetch(`${basePath}/dataplanes/${id}`, { method: 'DELETE' }),
};
