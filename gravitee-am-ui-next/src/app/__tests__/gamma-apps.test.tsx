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
import { buildApps } from '../gamma-apps';

describe('buildApps', () => {
    it('shows Access Management alone outside Gamma', () => {
        expect(buildApps(false).map(app => app.key)).toEqual(['am']);
    });

    it('pins Home, then lists Access Management among the Gamma modules in gamma-console order', () => {
        const apps = buildApps(true, [
            { id: 'platform', name: 'Platform', mfManifest: {} },
            { id: 'aim', name: 'Gravitee.io Gamma - Module - AI Management', mfManifest: {} },
            { id: 'backend-only', name: 'Backend only' },
            { id: 'zeta', name: 'Zeta Module', mfManifest: {} },
            { id: 'authz', name: 'Authorization Module', mfManifest: {} },
        ]);

        expect(apps.map(app => app.key)).toEqual(['home', 'aim', 'authz', 'am', 'platform', 'zeta']);
        expect(apps[0]).toMatchObject({ label: 'Home', pinned: true });
        expect(apps.find(app => app.key === 'aim')?.label).toBe('Agent Management');
        expect(apps.find(app => app.key === 'zeta')?.label).toBe('Zeta Module');
    });

    it('keeps Home when the module list is unavailable', () => {
        expect(buildApps(true, undefined).map(app => app.key)).toEqual(['home', 'am']);
    });
});
