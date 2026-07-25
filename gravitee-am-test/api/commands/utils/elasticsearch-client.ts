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
 * Minimal Elasticsearch access for the reporter specs: enough to assert what the gateway actually
 * wrote, and to plant a document the database reporter cannot possibly know about.
 */
const esUrl = (): string => process.env.AM_ELASTICSEARCH_URL ?? 'http://localhost:9200';

/** The endpoint AM's own containers use, which is not the one the test runner uses. */
export const internalElasticsearchUrl = (): string => process.env.AM_INTERNAL_ELASTICSEARCH_URL ?? 'http://elasticsearch:9200';

const request = async (method: string, path: string, body?: string): Promise<{ status: number; body: any }> => {
  const response = await fetch(`${esUrl()}${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body,
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : {} };
};

export const refreshIndices = async (indexPattern: string): Promise<void> => {
  await request('POST', `/${indexPattern}/_refresh`);
};

/**
 * Elasticsearch refuses a wildcard delete by default (`action.destructive_requires_name`), so the
 * matching indices are resolved to concrete names first — otherwise cleanup silently leaves the test
 * indices behind.
 */
export const deleteIndices = async (indexPattern: string): Promise<void> => {
  const { status, body } = await request('GET', `/_cat/indices/${indexPattern}?format=json&h=index`);
  if (status === 200 && Array.isArray(body) && body.length > 0) {
    await request('DELETE', `/${body.map((entry: any) => entry.index).join(',')}`);
  }
  await request('DELETE', `/_index_template/${indexPattern}`);
};

/**
 * Plants a composable index template. Used to occupy the slot the reporter needs: Elasticsearch
 * refuses two templates whose patterns overlap at the same priority, which is the way to make a
 * reporter fail for good rather than retry.
 */
export const putIndexTemplate = async (name: string, template: object): Promise<void> => {
  const { status, body } = await request('PUT', `/_index_template/${name}`, JSON.stringify(template));
  if (status !== 200) {
    throw new Error(`Failed to create index template ${name}: ${JSON.stringify(body)}`);
  }
};

export const indexDocument = async (index: string, id: string, document: object): Promise<void> => {
  const { status, body } = await request('PUT', `/${index}/_doc/${id}?refresh=true`, JSON.stringify(document));
  if (status !== 200 && status !== 201) {
    throw new Error(`Failed to index ${id} into ${index}: ${JSON.stringify(body)}`);
  }
};

/**
 * A daily index created moments ago can still be allocating its shards, and Elasticsearch fails the
 * whole search with `no_shard_available_action_exception` rather than returning partial results. That
 * is indistinguishable from "nothing written yet" to a caller, so it is reported the same way and the
 * pollers above simply come back around.
 */
const shardsNotReadyYet = (status: number, body: any): boolean =>
  status === 503 && JSON.stringify(body ?? {}).includes('no_shard_available_action_exception');

export const searchAudits = async (indexPattern: string, query: object): Promise<any[]> => {
  await refreshIndices(indexPattern);
  const { status, body } = await request('POST', `/${indexPattern}/_search`, JSON.stringify({ size: 50, query }));
  if (status === 404 || shardsNotReadyYet(status, body)) {
    return [];
  }
  if (status !== 200) {
    throw new Error(`Search on ${indexPattern} failed: ${JSON.stringify(body)}`);
  }
  return body.hits?.hits?.map((hit: any) => hit._source) ?? [];
};

export interface WaitOptions {
  timeoutMs?: number;
  intervalMs?: number;
}

/** Polls Elasticsearch until an audit matching the predicate shows up, or the timeout expires. */
export const waitForAuditInElasticsearch = async (
  indexPattern: string,
  domainId: string,
  predicate: (audit: any) => boolean,
  { timeoutMs = 30000, intervalMs = 500 }: WaitOptions = {},
): Promise<any> => {
  const deadline = Date.now() + timeoutMs;
  let seen: any[] = [];
  while (Date.now() < deadline) {
    seen = await searchAudits(indexPattern, { term: { referenceId: domainId } });
    const match = seen.find(predicate);
    if (match) {
      return match;
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(
    `No matching audit in ${indexPattern} for domain ${domainId} after ${timeoutMs}ms. Saw: ${JSON.stringify(seen.map((a) => a.type))}`,
  );
};

/** Asserts nothing turned up, by giving it a fair chance to and then checking it did not. */
export const expectNoAuditInElasticsearch = async (indexPattern: string, domainId: string, waitMs = 8000): Promise<void> => {
  await new Promise((resolve) => setTimeout(resolve, waitMs));
  const audits = await searchAudits(indexPattern, { term: { referenceId: domainId } });
  if (audits.length > 0) {
    throw new Error(`Expected no audit in ${indexPattern} for domain ${domainId}, but found ${audits.length}`);
  }
};
