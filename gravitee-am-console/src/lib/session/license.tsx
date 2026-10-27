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
import { Spinner } from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { createContext, type ReactNode, use, useMemo, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { UpgradeDialog } from '../../app/components/UpgradeDialog';
import { UpgradePanel } from '../../app/components/UpgradePanel';
import { getLicense, type License } from '../api/license';
import { AmFeature, type AmFeatureName, FEATURE_DESCRIPTIONS } from './license-features';

const NO_LICENSE: License = { features: [] };

const LicenseContext = createContext<License | null>(null);

export function upgradeUrl(feature: string): string {
    const params = new URLSearchParams({ utm_source: 'oss_am', utm_medium: feature, utm_campaign: 'oss_am_to_ee_am' });
    return `https://gravitee.io/self-hosted-trial?${params}`;
}

function isAmFeature(feature: string): feature is AmFeatureName {
    return Object.keys(FEATURE_DESCRIPTIONS).includes(feature);
}

export function featureDescription(feature: string): string {
    return FEATURE_DESCRIPTIONS[isAmFeature(feature) ? feature : AmFeature.AM_ENTERPRISE];
}

export interface LicenseState {
    readonly license: License;
    /** False for an absent or blank feature: an OSS plugin. */
    readonly isMissing: (feature?: string | null) => boolean;
}

export function useLicense(): LicenseState {
    const license = use(LicenseContext);
    if (!license) {
        throw new Error('useLicense() called outside <LicenseScope>');
    }
    return useMemo(() => ({ license, isMissing: feature => !!feature?.trim() && !(license.features ?? []).includes(feature) }), [license]);
}

interface LicenseScopeProps {
    readonly license: License;
    readonly children: ReactNode;
}

export function LicenseScope({ license, children }: LicenseScopeProps) {
    return <LicenseContext value={license}>{children}</LicenseContext>;
}

interface LoadLicenseScopeProps {
    readonly organizationId: string;
    readonly children: ReactNode;
}

/** A failed load counts as no license. */
export function LoadLicenseScope({ organizationId, children }: LoadLicenseScopeProps) {
    const { data, error } = useQuery({
        queryKey: ['license', organizationId],
        queryFn: () => getLicense(organizationId),
        staleTime: Infinity,
    });

    if (!data && !error) {
        return (
            <div className="flex h-screen items-center justify-center">
                <Spinner />
            </div>
        );
    }
    return <LicenseScope license={data ?? NO_LICENSE}>{children}</LicenseScope>;
}

interface RequireLicenseProps {
    readonly feature: string;
    readonly children?: ReactNode;
}

export function RequireLicense({ feature, children }: RequireLicenseProps) {
    const { isMissing } = useLicense();
    if (isMissing(feature)) {
        return <UpgradePanel feature={feature} description={featureDescription(feature)} />;
    }
    return children ?? <Outlet />;
}

interface LicenseGuardProps {
    readonly feature?: string | null;
    readonly children: ReactNode;
}

/** When the license lacks the feature, a click opens the upgrade dialog and never reaches the control's handler. */
export function LicenseGuard({ feature, children }: LicenseGuardProps) {
    const { isMissing } = useLicense();
    const [open, setOpen] = useState(false);
    if (!feature || !isMissing(feature)) {
        return children;
    }
    return (
        <>
            <span
                className="contents"
                onClickCapture={event => {
                    event.preventDefault();
                    event.stopPropagation();
                    setOpen(true);
                }}
            >
                {children}
            </span>
            <UpgradeDialog open={open} feature={feature} description={featureDescription(feature)} onOpenChange={setOpen} />
        </>
    );
}
