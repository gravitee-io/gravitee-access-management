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
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import {
    deleteBotDetection,
    getBotDetection,
    getBotDetectionSchema,
    listBotDetectionPlugins,
    updateBotDetection,
} from '../../../lib/api/bot-detection';
import { HttpError } from '../../../lib/api/http';
import { BotDetectionPage } from '../BotDetectionPage';
import { botDetectionsPath, currentDomain, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/bot-detection', () => ({
    deleteBotDetection: vi.fn(),
    getBotDetection: vi.fn(),
    getBotDetectionSchema: vi.fn(),
    listBotDetectionPlugins: vi.fn(),
    updateBotDetection: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_bot_detection_update', 'domain_bot_detection_delete'];
const botDetection = {
    id: 'b-1',
    name: 'reCAPTCHA',
    type: 'google-recaptcha-v3-am-bot-detection',
    detectionType: 'CAPTCHA',
    configuration: JSON.stringify({ siteKey: 'site-123', tokenParameterName: 'X-Recaptcha-Token' }),
};

function renderPage(permissions = ALL) {
    return renderRoutes(
        `${botDetectionsPath}/b-1`,
        <>
            <Route path={`${botDetectionsPath}/:botDetectionId`} element={<BotDetectionPage />} />
            <Route path={botDetectionsPath} element={<p>Bot detection list</p>} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getBotDetection).mockResolvedValue(botDetection);
    vi.mocked(listBotDetectionPlugins).mockResolvedValue([
        { id: 'google-recaptcha-v3-am-bot-detection', name: 'Google reCaptcha v3 Plugin' },
        { id: 'premium-am-bot-detection', name: 'Premium Bot Detection', feature: 'am-bot-detection-premium' },
    ]);
    vi.mocked(getBotDetectionSchema).mockResolvedValue({
        type: 'object',
        properties: {
            detectionType: { title: 'Detection Type', description: 'Type of bot detection', default: 'CAPTCHA', readOnly: true },
            siteKey: { type: 'string', title: 'Site key' },
            tokenParameterName: { type: 'string', title: 'Token parameter name' },
            minScore: { type: 'number', title: 'Minimum score', default: 0.5 },
        },
    });
});

describe('BotDetectionPage', () => {
    it('saves the name, type and serialized configuration with the schema defaults, as Angular does', async () => {
        vi.mocked(updateBotDetection).mockImplementation(async (_ref, _id, body) => ({ ...botDetection, ...body }));
        renderPage();

        expect(await screen.findByRole('textbox', { name: 'Detection type' })).toHaveValue('CAPTCHA');
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Captcha' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateBotDetection).toHaveBeenCalledWith(ref, 'b-1', {
                name: 'Captcha',
                type: 'google-recaptcha-v3-am-bot-detection',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(updateBotDetection).mock.calls[0][2].configuration)).toEqual({
            siteKey: 'site-123',
            tokenParameterName: 'X-Recaptcha-Token',
            minScore: 0.5,
        });
        expect(getBotDetection).toHaveBeenCalledWith(ref, 'b-1');
        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled());
    });

    it('requires a name', async () => {
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: ' ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText('Name is required')).toBeInTheDocument();
        expect(updateBotDetection).not.toHaveBeenCalled();
    });

    it('shows the reCAPTCHA v3 integration snippets from the saved site key', async () => {
        renderPage();

        expect(await screen.findByText('Integration')).toBeInTheDocument();
        expect(screen.getByText(/grecaptcha\.execute\('site-123'/)).toBeInTheDocument();
        expect(screen.getByText(/configured parameter X-Recaptcha-Token/)).toBeInTheDocument();
    });

    it('copies the snippets without a save', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
        renderPage();

        await screen.findByText('Integration');
        for (const button of screen.getAllByRole('button', { name: /copy/i })) fireEvent.click(button);

        await waitFor(() => expect(writeText).toHaveBeenCalledWith(expect.stringContaining("grecaptcha.execute('site-123'")));
        expect(updateBotDetection).not.toHaveBeenCalled();
    });

    it('deletes the bot detection after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteBotDetection).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        fireEvent.click(
            within(await screen.findByRole('dialog', { name: 'Delete bot detection' })).getByRole('button', { name: 'Delete' }),
        );

        await waitFor(() => expect(deleteBotDetection).toHaveBeenCalledWith(ref, 'b-1'));
        expect(await screen.findByText('Bot detection list')).toBeInTheDocument();
    });

    it('hides save and delete without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not save, when the licence lacks the plugin feature', async () => {
        vi.mocked(getBotDetection).mockResolvedValue({ ...botDetection, type: 'premium-am-bot-detection' });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Premium' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateBotDetection).not.toHaveBeenCalled();
    });

    it('shows the not-found panel for a deleted bot detection', async () => {
        vi.mocked(getBotDetection).mockRejectedValueOnce(new HttpError(404, 'Bot detection not found'));
        renderPage();

        expect(await screen.findByText('Bot detection not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to bot detection' })).toHaveAttribute('href', botDetectionsPath);
    });
});
