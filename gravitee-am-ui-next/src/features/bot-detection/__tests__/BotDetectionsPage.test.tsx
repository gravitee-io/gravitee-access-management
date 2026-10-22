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
import { screen } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { listBotDetections } from '../../../lib/api/bot-detection';
import { BotDetectionsPage } from '../BotDetectionsPage';
import { botDetectionsPath, currentDomain, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/bot-detection', () => ({ listBotDetections: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_bot_detection_create', 'domain_bot_detection_read'];

function renderPage(permissions = ALL) {
    return renderRoutes(botDetectionsPath, <Route path={botDetectionsPath} element={<BotDetectionsPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listBotDetections).mockResolvedValue([
        { id: 'b-1', name: 'reCAPTCHA', type: 'google-recaptcha-v3-am-bot-detection', detectionType: 'CAPTCHA' },
        { id: 'b-2', name: 'Custom', type: 'custom-am-bot-detection', detectionType: 'OTHER' },
    ]);
});

describe('BotDetectionsPage', () => {
    it('lists the bot detections of the domain with the Angular type names and a link to each', async () => {
        renderPage();

        expect(await screen.findByRole('link', { name: 'reCAPTCHA' })).toHaveAttribute('href', `${botDetectionsPath}/b-1`);
        expect(screen.getByText('Google reCAPTCHA v3')).toBeInTheDocument();
        expect(screen.getByText('custom-am-bot-detection')).toBeInTheDocument();
        expect(screen.getByText('CAPTCHA')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add bot detection' })).toHaveAttribute('href', `${botDetectionsPath}/new`);
        expect(listBotDetections).toHaveBeenCalledWith(ref);
    });

    it('shows the first-use state when the domain has no bot detection', async () => {
        vi.mocked(listBotDetections).mockResolvedValueOnce([]);
        renderPage();

        expect(await screen.findByText('No bot detection yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add bot detection' })).toBeInTheDocument();
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listBotDetections).mockRejectedValueOnce(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Could not load the bot detections.')).toBeInTheDocument();
        expect(screen.queryByText('No bot detection yet')).not.toBeInTheDocument();
    });

    it('hides the add button and the links without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByText('reCAPTCHA')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'reCAPTCHA' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Add bot detection' })).not.toBeInTheDocument();
    });
});
