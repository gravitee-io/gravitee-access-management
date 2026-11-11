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
import { searchApplicationAnalytics } from '../analytics';
import {
    createApplication,
    createCimdApplication,
    deleteApplication,
    getApplication,
    listDomainApplications,
    patchApplication,
    updateApplicationType,
    validateCimd,
} from '../applications';
import { http } from '../http';
import { getSpelGrammar } from '../management-api';
import { listApplicationPermissions } from '../permissions';

vi.mock('../http', () => ({ http: vi.fn() }));

const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const domainPath = '/organizations/DEFAULT/environments/env-1/domains/d1';
const TYPES = '&type=WEB&type=NATIVE&type=BROWSER&type=SERVICE&type=RESOURCE_SERVER';

function lastCall() {
    const calls = vi.mocked(http).mock.calls;
    const [path, init] = calls[calls.length - 1] ?? [];
    return { path, method: init?.method, body: init?.body ? JSON.parse(init.body as string) : undefined };
}

describe('applications api', () => {
    beforeEach(() => {
        vi.mocked(http).mockReset();
        vi.mocked(http).mockResolvedValue({});
    });

    it('searches the applications that are not agents, a page at a time, the last updated first', async () => {
        await listDomainApplications(ref, 2, 25, '', { field: 'updatedAt', dir: 'desc' });

        expect(lastCall().path).toBe(`${domainPath}/applications/search?page=2&limit=25&sort=updatedAt&dir=desc${TYPES}`);
    });

    it('searches the applications with a contains match on the name or the client id', async () => {
        await listDomainApplications(ref, 0, 25, 'my app', { field: 'updatedAt', dir: 'desc' });

        expect(lastCall().path).toBe(`${domainPath}/applications/search?page=0&limit=25&sort=updatedAt&dir=desc&q=*my%20app*${TYPES}`);
    });

    it('searches the applications in the sort the user picked', async () => {
        await listDomainApplications(ref, 0, 10, '', { field: 'name', dir: 'asc' });

        expect(lastCall().path).toBe(`${domainPath}/applications/search?page=0&limit=10&sort=name&dir=asc${TYPES}`);
    });

    it('reads an application by its id', async () => {
        await getApplication(ref, 'app/1');

        expect(lastCall().path).toBe(`${domainPath}/applications/app%2F1`);
    });

    it('creates an application with the fields of the form', async () => {
        await createApplication(ref, { name: 'Portal', type: 'WEB', redirectUris: ['https://portal/cb'] });

        expect(lastCall()).toEqual({
            path: `${domainPath}/applications`,
            method: 'POST',
            body: { name: 'Portal', type: 'WEB', redirectUris: ['https://portal/cb'] },
        });
    });

    it('validates a client metadata document', async () => {
        await validateCimd(ref, 'https://client.example/metadata.json');

        expect(lastCall()).toEqual({
            path: `${domainPath}/cimd/validate`,
            method: 'POST',
            body: { url: 'https://client.example/metadata.json' },
        });
    });

    it('creates an application from a client metadata document', async () => {
        await createCimdApplication(ref, {
            name: 'Portal',
            type: 'WEB',
            cimdUrl: 'https://client.example/metadata.json',
            clientName: 'Portal',
        });

        expect(lastCall()).toEqual({
            path: `${domainPath}/cimd/applications`,
            method: 'POST',
            body: { name: 'Portal', type: 'WEB', cimdUrl: 'https://client.example/metadata.json', clientName: 'Portal' },
        });
    });

    it('patches an application', async () => {
        await patchApplication(ref, 'app-1', { metadata: { team: 'blue' } });

        expect(lastCall()).toEqual({ path: `${domainPath}/applications/app-1`, method: 'PATCH', body: { metadata: { team: 'blue' } } });
    });

    it('changes the type of an application', async () => {
        await updateApplicationType(ref, 'app-1', 'SERVICE');

        expect(lastCall()).toEqual({ path: `${domainPath}/applications/app-1/type`, method: 'PUT', body: { type: 'SERVICE' } });
    });

    it('deletes an application', async () => {
        await deleteApplication(ref, 'app-1');

        expect(lastCall()).toEqual({ path: `${domainPath}/applications/app-1`, method: 'DELETE', body: undefined });
    });

    it('reads the permissions of the signed-in user on an application', async () => {
        await listApplicationPermissions(ref, 'app-1');

        expect(lastCall().path).toBe(`${domainPath}/applications/app-1/members/permissions`);
    });

    it('queries the analytics of an application', async () => {
        await searchApplicationAnalytics(ref, 'app-1', { type: 'count', field: 'user_login', from: 1, to: 2, interval: 3 });

        expect(lastCall().path).toBe(`${domainPath}/applications/app-1/analytics?type=count&field=user_login&from=1&to=2&interval=3`);
    });

    it('reads the expression language grammar', async () => {
        await getSpelGrammar();

        expect(lastCall().path).toBe('/platform/configuration/spel/grammar');
    });
});
