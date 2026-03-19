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
import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { AuthorizationEngineService } from '../../../services/authorization-engine.service';
import { AuthorizationBundleService } from '../../../services/authorization-bundle.service';
import { AuthorizationSchemaService } from '../../../services/authorization-schema.service';
import { OrganizationService } from '../../../services/organization.service';
import { SnackbarService } from '../../../services/snackbar.service';

interface AuthorizationEngine {
  id?: string;
  name: string;
  type: string;
  configuration: string;
}

interface Plugin {
  id: string;
  name: string;
  displayName?: string;
  icon?: string;
}

interface Bundle {
  id: string;
  name: string;
}

interface Schema {
  id: string;
  name: string;
  latestVersion: number;
}

@Component({
  selector: 'app-sidecar',
  standalone: false,
  templateUrl: './sidecar.component.html',
  styleUrls: ['./sidecar.component.scss'],
})
export class SidecarComponent implements OnInit, OnDestroy {
  domainId: string;
  domainPath: string;
  engineId: string;
  authorizationEngine: AuthorizationEngine = { name: '', type: '', configuration: '' };
  plugin: Plugin | null = null;

  configuration: Record<string, any> = {};
  draftConfiguration: Record<string, any> = {};
  originalName: string = '';

  // Bundle
  selectedBundleId: string = '';
  originalBundleId: string = '';
  bundles: Bundle[] = [];

  // Schema (independent from bundle)
  schemas: Schema[] = [];
  schemaVersions: any[] = [];
  selectedSchemaId: string = '';
  originalSchemaId: string = '';
  selectedSchemaVersion: number = 0;
  selectedSchemaPinToLatest: boolean = true;

  configurationSchema: Record<string, any> = {};
  configurationIsValid = false;
  isSaving = false;

  private authorizationEnginePlugins: Record<string, Plugin> = {};
  private subscriptions = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private authorizationEngineService: AuthorizationEngineService,
    private authorizationBundleService: AuthorizationBundleService,
    private authorizationSchemaService: AuthorizationSchemaService,
    private organizationService: OrganizationService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.domainPath = this.route.snapshot.data['domain']?.path || '';
    this.engineId = this.route.snapshot.params['engineId'];
    this.authorizationEnginePlugins = this.toPluginMap(this.route.snapshot.data['authorizationEnginePlugins']);

    this.loadBundles();
    this.loadSchemas();
    this.load();
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
  }

  load() {
    this.subscriptions.add(
      this.authorizationEngineService.get(this.domainId, this.engineId).subscribe({
        next: (engine) => {
          this.authorizationEngine = engine;
          this.plugin = this.authorizationEnginePlugins?.[engine.type];
          const config = JSON.parse(engine.configuration || '{}');

          this.configuration = { ...config };
          this.draftConfiguration = { ...config };
          this.originalName = engine.name;

          // Bundle
          this.selectedBundleId = config.bundleId || '';
          this.originalBundleId = config.bundleId || '';

          // Schema
          this.selectedSchemaId = config.schemaId || '';
          this.originalSchemaId = config.schemaId || '';
          this.selectedSchemaVersion = config.schemaVersion || 0;
          this.selectedSchemaPinToLatest = config.schemaPinToLatest !== false;

          if (this.selectedSchemaId) {
            this.loadSchemaVersions(this.selectedSchemaId);
          }

          this.loadAuthorizationEngineSchema(engine.type);
        },
        error: () => {
          this.snackbarService.open('Failed to load authorization engine');
        },
      }),
    );
  }

  loadBundles() {
    this.subscriptions.add(
      this.authorizationBundleService.findByDomain(this.domainId).subscribe({
        next: (bundles) => {
          this.bundles = bundles || [];
        },
        error: () => {
          this.bundles = [];
        },
      }),
    );
  }

  loadSchemas() {
    this.subscriptions.add(
      this.authorizationSchemaService.findByDomain(this.domainId).subscribe({
        next: (schemas) => {
          this.schemas = schemas || [];
        },
        error: () => {
          this.schemas = [];
        },
      }),
    );
  }

  loadSchemaVersions(schemaId: string) {
    this.subscriptions.add(
      this.authorizationSchemaService.getVersions(this.domainId, schemaId).subscribe({
        next: (v) => {
          this.schemaVersions = v.sort((a: any, b: any) => b.version - a.version);
        },
        error: () => {
          this.schemaVersions = [];
        },
      }),
    );
  }

  loadAuthorizationEngineSchema(engineType: string) {
    this.subscriptions.add(
      this.organizationService.authorizationEngineSchema(engineType).subscribe({
        next: (schema) => {
          this.configurationSchema = schema;
        },
        error: () => {
          this.snackbarService.open('Failed to load authorization engine schema');
        },
      }),
    );
  }

  onBundleChanged() {
    this.draftConfiguration = { ...this.draftConfiguration, bundleId: this.selectedBundleId };
  }

  onSchemaChanged() {
    this.draftConfiguration = {
      ...this.draftConfiguration,
      schemaId: this.selectedSchemaId,
      schemaVersion: this.selectedSchemaVersion,
      schemaPinToLatest: this.selectedSchemaPinToLatest,
    };
    if (this.selectedSchemaId) {
      this.loadSchemaVersions(this.selectedSchemaId);
      const schema = this.schemas.find((s) => s.id === this.selectedSchemaId);
      if (schema) {
        this.selectedSchemaVersion = schema.latestVersion;
      }
    } else {
      this.schemaVersions = [];
      this.selectedSchemaVersion = 0;
    }
  }

  isConfigurationDirty(): boolean {
    const configChanged = JSON.stringify(this.draftConfiguration) !== JSON.stringify(this.configuration);
    const nameChanged = this.authorizationEngine?.name !== this.originalName;
    const bundleChanged = this.selectedBundleId !== this.originalBundleId;
    const schemaChanged = this.selectedSchemaId !== this.originalSchemaId;
    return configChanged || nameChanged || bundleChanged || schemaChanged;
  }

  onConfigurationChanged(configurationWrapper: { isValid: boolean; configuration: Record<string, any> }) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.draftConfiguration = {
      ...(configurationWrapper.configuration || {}),
      bundleId: this.selectedBundleId,
      schemaId: this.selectedSchemaId,
      schemaVersion: this.selectedSchemaVersion,
      schemaPinToLatest: this.selectedSchemaPinToLatest,
    };
  }

  saveConfiguration() {
    if (!this.isConfigurationDirty() || this.isSaving) {
      return;
    }

    this.isSaving = true;

    const finalConfig = {
      ...this.draftConfiguration,
      bundleId: this.selectedBundleId,
      schemaId: this.selectedSchemaId,
      schemaVersion: this.selectedSchemaVersion,
      schemaPinToLatest: this.selectedSchemaPinToLatest,
    };

    const updatedEngine: AuthorizationEngine = {
      ...this.authorizationEngine,
      name: this.authorizationEngine.name,
      configuration: JSON.stringify(finalConfig),
    };

    this.subscriptions.add(
      this.authorizationEngineService
        .update(this.domainId, this.engineId, updatedEngine)
        .pipe(
          finalize(() => {
            this.isSaving = false;
          }),
        )
        .subscribe({
          next: (engine) => {
            this.authorizationEngine = engine;
            const config = JSON.parse(engine.configuration || '{}');
            this.configuration = { ...config };
            this.draftConfiguration = { ...config };
            this.originalName = engine.name;
            this.selectedBundleId = config.bundleId || '';
            this.originalBundleId = config.bundleId || '';
            this.selectedSchemaId = config.schemaId || '';
            this.originalSchemaId = config.schemaId || '';
            this.selectedSchemaVersion = config.schemaVersion || 0;
            this.selectedSchemaPinToLatest = config.schemaPinToLatest !== false;
            this.snackbarService.open('Configuration saved successfully');
          },
        }),
    );
  }

  private toPluginMap(plugins: Plugin[] | Record<string, Plugin>): Record<string, Plugin> {
    if (!Array.isArray(plugins)) {
      return plugins || {};
    }
    return plugins.reduce(
      (acc, plugin) => {
        if (plugin?.id) {
          acc[plugin.id] = plugin;
        }
        return acc;
      },
      {} as Record<string, Plugin>,
    );
  }
}
