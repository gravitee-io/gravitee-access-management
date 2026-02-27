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

import { SidecarService } from '../../../services/sidecar.service';
import { AuthorizationEngineService } from '../../../services/authorization-engine.service';
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

@Component({
  selector: 'app-sidecar',
  standalone: false,
  templateUrl: './sidecar.component.html',
  styleUrls: ['./sidecar.component.scss'],
})
export class SidecarComponent implements OnInit, OnDestroy {
  domainId: string;
  engineId: string;
  authorizationEngine: AuthorizationEngine = { name: '', type: '', configuration: '' };
  plugin: Plugin | null = null;

  configuration: Record<string, any> = {};
  draftConfiguration: Record<string, any> = {};
  originalName: string = '';
  configurationSchema: Record<string, any> = {};
  configurationIsValid = false;
  isSaving = false;

  healthStatus: string | null = null;
  healthReady: boolean | null = null;
  isCheckingHealth = false;
  hasLoadError = false;

  private authorizationEnginePlugins: Record<string, Plugin> = {};
  private subscriptions = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private sidecarService: SidecarService,
    private authorizationEngineService: AuthorizationEngineService,
    private organizationService: OrganizationService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.engineId = this.route.snapshot.params['engineId'];
    this.authorizationEnginePlugins = this.toPluginMap(this.route.snapshot.data['authorizationEnginePlugins']);

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

          this.loadAuthorizationEngineSchema(engine.type);
          this.checkHealth();
        },
        error: () => {
          this.snackbarService.open('Failed to load authorization engine');
          this.hasLoadError = true;
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

  checkHealth() {
    this.isCheckingHealth = true;
    this.subscriptions.add(
      this.sidecarService.health(this.domainId, this.engineId).subscribe({
        next: (response) => {
          this.healthStatus = response.status;
          this.healthReady = response.status === 'UP';
          this.isCheckingHealth = false;
          this.hasLoadError = false;
        },
        error: () => {
          this.healthStatus = 'UNREACHABLE';
          this.healthReady = false;
          this.isCheckingHealth = false;
          this.hasLoadError = true;
        },
      }),
    );
  }

  isConfigurationDirty(): boolean {
    const configChanged = JSON.stringify(this.draftConfiguration) !== JSON.stringify(this.configuration);
    const nameChanged = this.authorizationEngine?.name !== this.originalName;
    return configChanged || nameChanged;
  }

  onConfigurationChanged(configurationWrapper: { isValid: boolean; configuration: Record<string, any> }) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.draftConfiguration = configurationWrapper.configuration || {};
  }

  saveConfiguration() {
    if (!this.isConfigurationDirty() || this.isSaving) {
      return;
    }

    this.isSaving = true;

    const updatedEngine: AuthorizationEngine = {
      ...this.authorizationEngine,
      name: this.authorizationEngine.name,
      configuration: JSON.stringify(this.draftConfiguration),
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
            this.snackbarService.open('Configuration saved successfully');

            this.checkHealth();
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
