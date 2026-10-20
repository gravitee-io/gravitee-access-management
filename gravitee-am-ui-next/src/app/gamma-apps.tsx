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
import type { AppDefinition } from '@gravitee/graphene-core';
import {
    GioAgentManagementIcon,
    GioApiManagementIcon,
    GioAuthorizationIcon,
    GioDeveloperPortalIcon,
    GioEdgeManagementIcon,
    GioEventApiManagementIcon,
    GioHomeIcon,
    GioIamIcon,
    GioPlatformIcon,
    type LucideIcon,
} from '@gravitee/graphene-core/icons';
import type { GammaModule } from '../lib/api/gamma-api';

export const AM_APP_KEY = 'am';
export const HOME_APP_KEY = 'home';

interface ModuleProduct {
    readonly id: string;
    readonly label: string;
    readonly tagline: string;
    readonly icon: LucideIcon;
}

// Mirrors MODULE_CATALOG and MODULE_ICONS of gamma-console, with Access Management in its place, so both switchers
// present the products in the same order with the same names. Unknown modules go last, under their plugin name.
const PRODUCTS: ModuleProduct[] = [
    { id: 'aim', label: 'Agent Management', tagline: 'Govern AI agents, MCPs, and LLMs', icon: GioAgentManagementIcon },
    { id: 'apim', label: 'API Management', tagline: 'Design, deploy, and govern HTTP APIs', icon: GioApiManagementIcon },
    {
        id: 'esm',
        label: 'Event Stream Management',
        tagline: 'Manage Kafka clusters, services, and event mesh',
        icon: GioEventApiManagementIcon,
    },
    { id: 'authz', label: 'Authorization Management', tagline: 'Fine-grained authorization policies', icon: GioAuthorizationIcon },
    { id: AM_APP_KEY, label: 'Access Management', tagline: 'Identity and access for apps and agents', icon: GioIamIcon },
    { id: 'portals', label: 'Developer Portals', tagline: 'Design and manage developer portal experiences', icon: GioDeveloperPortalIcon },
    { id: 'edge', label: 'Edge Management', tagline: 'Monitor and manage Edge Daemons', icon: GioEdgeManagementIcon },
    { id: 'platform', label: 'Platform Management', tagline: 'Apps, subscriptions, and usage', icon: GioPlatformIcon },
];

const position = (id: string) => {
    const index = PRODUCTS.findIndex(product => product.id === id);
    return index === -1 ? PRODUCTS.length : index;
};

function toApp(id: string, fallbackName: string): AppDefinition {
    const product = PRODUCTS.find(candidate => candidate.id === id);
    const Icon = product?.icon ?? GioPlatformIcon;
    return {
        key: id,
        label: product?.label ?? fallbackName,
        description: product?.tagline ?? fallbackName,
        icon: <Icon className="size-5" />,
    };
}

/**
 * The switcher entries: a pinned Home back to gamma-console when AM runs in Gamma, then Access Management and the
 * Gamma modules that ship a UI, in gamma-console's order. Without Gamma it is Access Management alone.
 */
export function buildApps(inGamma: boolean, modules: readonly GammaModule[] = []): AppDefinition[] {
    const am = toApp(AM_APP_KEY, 'Access Management');
    if (!inGamma) {
        return [am];
    }
    const home = { key: HOME_APP_KEY, label: 'Home', icon: <GioHomeIcon className="size-5" />, pinned: true };
    const others = modules.filter(module => module.mfManifest && module.id !== AM_APP_KEY).map(module => toApp(module.id, module.name));
    const ordered = [am, ...others].sort((a, b) => position(a.key) - position(b.key) || a.key.localeCompare(b.key));
    return [home, ...ordered];
}
