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
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { HttpError } from '../../api/http';
import { getLicense } from '../../api/license';
import { getCurrentUser, listEnvironments } from '../../api/management-api';
import { SessionGate, useSession } from '../session';

vi.mock('../../api/management-api', () => ({ getCurrentUser: vi.fn(), listEnvironments: vi.fn() }));
vi.mock('../../api/license', () => ({ getLicense: vi.fn() }));

function Probe() {
    const { user, environments } = useSession();
    return (
        <p>
            {user.sub} in {environments.map(env => env.name).join(', ')}
        </p>
    );
}

function renderGate() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <SessionGate>
                <Probe />
            </SessionGate>
        </QueryClientProvider>,
    );
}

describe('SessionGate', () => {
    beforeEach(() => {
        vi.mocked(getLicense).mockResolvedValue({ features: [] });
    });

    it('renders the children with the user and the environments of the organization', async () => {
        vi.mocked(getCurrentUser).mockResolvedValue({ sub: 'admin', org: 'DEFAULT', permissions: [] });
        vi.mocked(listEnvironments).mockResolvedValue([{ id: 'env-1', name: 'Production', hrids: ['prod'] }]);

        renderGate();

        expect(await screen.findByText('admin in Production')).toBeInTheDocument();
        expect(listEnvironments).toHaveBeenCalledWith('DEFAULT');
    });

    it('shows the error when the user cannot be loaded', async () => {
        vi.mocked(getCurrentUser).mockRejectedValue(new HttpError(500, 'Boom'));

        renderGate();

        expect(await screen.findByText('Could not load the current user.')).toBeInTheDocument();
        expect(screen.getByText('Boom')).toBeInTheDocument();
    });

    it('shows no error on 401, while the login chain takes the browser away', async () => {
        vi.mocked(getCurrentUser).mockRejectedValue(new HttpError(401, 'Unauthorized'));

        renderGate();

        await vi.waitFor(() => expect(getCurrentUser).toHaveBeenCalled());
        expect(screen.queryByText('Could not load the current user.')).not.toBeInTheDocument();
    });
});
