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
import type { NavGroup } from '@gravitee/graphene-core';
import { AppWindowIcon, ArrowLeftIcon, FileCodeIcon, ShieldIcon, UsersIcon } from '@gravitee/graphene-core/icons';

export const NAV_GROUPS: NavGroup[] = [
    {
        label: 'Security',
        items: [{ key: 'domains', title: 'Domains', icon: ShieldIcon }],
    },
];

/** Navigation inside a security domain. `domains` leads back to the environment's domain list. */
export const DOMAIN_NAV_GROUPS: NavGroup[] = [
    { label: 'Security', items: [{ key: 'domains', title: 'All domains', icon: ArrowLeftIcon }] },
    {
        label: 'Domain',
        items: [
            { key: 'applications', title: 'Applications', icon: AppWindowIcon },
            { key: 'identity-providers', title: 'Identity providers', icon: UsersIcon },
            { key: 'login-form', title: 'Login form', icon: FileCodeIcon },
        ],
    },
];
