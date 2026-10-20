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
import { render, screen } from '@testing-library/react';
import { HttpError } from '../../../lib/api/http';
import { ErrorPanel } from '../ErrorPanel';
import { NotFoundPanel } from '../NotFoundPanel';

describe('ErrorPanel', () => {
    it('names what failed and shows the server message', () => {
        render(<ErrorPanel description="Could not load the application." error={new HttpError(500, 'Internal error')} />);

        expect(screen.getByRole('alert')).toHaveTextContent('Unable to load');
        expect(screen.getByText('Could not load the application.')).toBeInTheDocument();
        expect(screen.getByText('Internal error')).toBeInTheDocument();
    });
});

describe('NotFoundPanel', () => {
    it('shows the title, the description and the action', () => {
        render(
            <NotFoundPanel
                title="Application not found"
                description="No application with this id exists in Acme."
                action={<a href="/apps">Back to applications</a>}
            />,
        );

        expect(screen.getByText('Application not found')).toBeInTheDocument();
        expect(screen.getByText('No application with this id exists in Acme.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to applications' })).toHaveAttribute('href', '/apps');
    });
});
