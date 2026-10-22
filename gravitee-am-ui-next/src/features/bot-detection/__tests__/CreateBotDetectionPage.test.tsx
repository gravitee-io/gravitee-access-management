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
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { createBotDetection, getBotDetectionSchema, listBotDetectionPlugins } from '../../../lib/api/bot-detection';
import { CreateBotDetectionPage } from '../CreateBotDetectionPage';
import { botDetectionsPath, currentDomain, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/bot-detection', () => ({
    createBotDetection: vi.fn(),
    getBotDetectionSchema: vi.fn(),
    listBotDetectionPlugins: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

function renderPage() {
    return renderRoutes(
        `${botDetectionsPath}/new`,
        <>
            <Route path={`${botDetectionsPath}/new`} element={<CreateBotDetectionPage />} />
            <Route path={`${botDetectionsPath}/:botDetectionId`} element={<p>Detail page</p>} />
        </>,
        [],
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listBotDetectionPlugins).mockResolvedValue([
        { id: 'google-recaptcha-v3-am-bot-detection', name: 'Google reCaptcha v3 Plugin' },
        { id: 'premium-am-bot-detection', name: 'Premium Bot Detection', feature: 'am-bot-detection-premium' },
    ]);
    vi.mocked(getBotDetectionSchema).mockResolvedValue({
        type: 'object',
        properties: {
            detectionType: { title: 'Detection Type', description: 'Type of bot detection', default: 'CAPTCHA', readOnly: true },
            siteKey: { type: 'string', title: 'Site Key' },
            minScore: { type: 'number', title: 'Minimum score', default: 0.5 },
        },
    });
    vi.mocked(createBotDetection).mockResolvedValue({
        id: 'b-9',
        name: 'reCAPTCHA',
        type: 'google-recaptcha-v3-am-bot-detection',
        configuration: '{}',
    });
});

describe('CreateBotDetectionPage', () => {
    it('names the plugins as Angular does and locks the ones the licence lacks', async () => {
        renderPage();

        expect(await screen.findByRole('radio', { name: 'Google reCAPTCHA v3' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Premium Bot Detection, Enterprise' })).toBeInTheDocument();
    });

    it('shows the detection type from the schema default on the settings step', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'Google reCAPTCHA v3' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));

        await waitFor(() => expect(screen.getByRole('textbox', { name: 'Detection type' })).toHaveValue('CAPTCHA'));
        expect(screen.getByRole('textbox', { name: 'Detection type' })).toBeDisabled();
    });

    it('creates the bot detection with the schema detection type and defaults, then opens it', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'Google reCAPTCHA v3' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'reCAPTCHA' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Site Key/ }), { target: { value: 'site-123' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createBotDetection).toHaveBeenCalledWith(ref, {
                name: 'reCAPTCHA',
                type: 'google-recaptcha-v3-am-bot-detection',
                detectionType: 'CAPTCHA',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(createBotDetection).mock.calls[0][1].configuration)).toEqual({
            siteKey: 'site-123',
            minScore: 0.5,
        });
        expect(getBotDetectionSchema).toHaveBeenCalledWith('google-recaptcha-v3-am-bot-detection');
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });
});
