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
import { Injectable } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { map, shareReplay, switchMap } from 'rxjs/operators';
import { GioLicenseService, LicenseOptions } from '@gravitee/ui-particles-angular';

import { OrganizationService } from './organization.service';

/** A blank/whitespace manifest feature means "OSS plugin" — treat it as no feature. */
const normalizeFeature = (feature?: string): string | undefined => (feature && feature.trim().length > 0 ? feature : undefined);

/** A plugin catalog item decorated with the license state the creation cards bind to. */
export interface LicensedPlugin {
  /** Feature-only license options for the guard/upgrade dialog (undefined feature ⇒ ungated). */
  licenseOptions?: LicenseOptions;
  /** Whether the card should render locked (upsell) rather than selectable. */
  isMissing$: Observable<boolean>;
}

/**
 * Plugin categories that the backend license gate can restrict for write operations.
 */
export type PluginCategory =
  | 'identity_provider'
  | 'certificate'
  | 'factor'
  | 'resource'
  | 'extension_grant'
  | 'bot_detection'
  | 'device_identifier'
  | 'authdevice_notifier'
  | 'reporter'
  | 'authorization_engine'
  | 'notifier';

/**
 * Resolves, for a given plugin instance type, whether the effective license grants its declared
 * feature — mirroring the backend {@code PluginLicenseGate}.
 */
@Injectable()
export class PluginFeatureService {
  private readonly catalogCache = new Map<PluginCategory, Observable<any[]>>();

  constructor(
    private readonly organizationService: OrganizationService,
    private readonly licenseService: GioLicenseService,
  ) {}

  /**
   * Resolves the license feature declared by the plugin of the given type, or undefined when the
   * type is unknown or the plugin is OSS (no feature).
   */
  getFeature$(category: PluginCategory, type: string): Observable<string | undefined> {
    if (!type) {
      return of(undefined);
    }
    return this.catalog$(category).pipe(map((plugins) => normalizeFeature(plugins?.find((plugin) => plugin.id === type)?.feature)));
  }

  /**
   * Whether the license effective for this node does not grant the feature required to write an
   * instance of the given plugin type. False for OSS plugins.
   */
  isMissingFeatureForType$(category: PluginCategory, type: string): Observable<boolean> {
    return this.getFeature$(category, type).pipe(switchMap((feature) => this.licenseService.isMissingFeature$(feature)));
  }

  /**
   * Decorates a plugin catalog (as consumed by the creation cards) with the license state to bind:
   * each item is gated on its declared feature.
   */
  decorateCatalog$<T extends { feature?: string }>(plugins: T[]): Observable<(T & LicensedPlugin)[]> {
    return of(
      plugins.map((plugin) => {
        const feature = normalizeFeature(plugin.feature);
        return { ...plugin, licenseOptions: { feature }, isMissing$: this.licenseService.isMissingFeature$(feature) };
      }),
    );
  }

  private catalog$(category: PluginCategory): Observable<any[]> {
    let cached = this.catalogCache.get(category);
    if (!cached) {
      cached = this.fetchCatalog(category).pipe(shareReplay({ bufferSize: 1, refCount: false }));
      this.catalogCache.set(category, cached);
    }
    return cached;
  }

  private fetchCatalog(category: PluginCategory): Observable<any[]> {
    switch (category) {
      case 'identity_provider':
        // merge built-in and social/enterprise IdPs, which are served by separate endpoints
        return forkJoin([this.organizationService.identities(), this.organizationService.socialIdentities()]).pipe(
          map(([builtIn, social]) => [...(builtIn ?? []), ...(social ?? [])]),
        );
      case 'certificate':
        return this.organizationService.certificates();
      case 'factor':
        return this.organizationService.factors();
      case 'resource':
        return this.organizationService.resources();
      case 'extension_grant':
        return this.organizationService.extensionGrants();
      case 'bot_detection':
        return this.organizationService.botDetections();
      case 'device_identifier':
        return this.organizationService.deviceIdentifiers();
      case 'authdevice_notifier':
        return this.organizationService.deviceNotifiers();
      case 'reporter':
        return this.organizationService.reporterPlugins();
      case 'authorization_engine':
        return this.organizationService.authorizationEngines(false);
      case 'notifier':
        return this.organizationService.notifiers();
    }
  }
}
