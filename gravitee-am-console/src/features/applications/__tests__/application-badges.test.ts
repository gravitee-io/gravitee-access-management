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
import { AppWindowIcon, CircleCheckIcon, CircleStopIcon, GlobeIcon, ServerIcon } from '@gravitee/graphene-core/icons';
import { applicationStatusBadge, applicationTypeBadge } from '../application-badges';

describe('application badges', () => {
    it('shows the type with its icon', () => {
        expect(applicationTypeBadge('web')).toMatchObject({ label: 'Web', variant: 'outline', icon: GlobeIcon });
        expect(applicationTypeBadge('browser')).toMatchObject({ label: 'Single-Page App', icon: AppWindowIcon });
        expect(applicationTypeBadge('service')).toMatchObject({ label: 'Backend to Backend', icon: ServerIcon });
    });

    it('shows an enabled application in the success colour', () => {
        expect(applicationStatusBadge({ enabled: true })).toEqual({ label: 'Enabled', variant: 'success', icon: CircleCheckIcon });
    });

    it('shows a disabled application, also a template, as disabled', () => {
        expect(applicationStatusBadge({ enabled: false, template: true })).toEqual({
            label: 'Disabled',
            variant: 'secondary',
            icon: CircleStopIcon,
        });
    });

    it('shows an enabled template as a template', () => {
        expect(applicationStatusBadge({ enabled: true, template: true })).toEqual({ label: 'Template', variant: 'outline' });
    });
});
