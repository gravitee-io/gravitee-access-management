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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthorizationEngineService } from '../../services/authorization-engine.service';
import { SnackbarService } from '../../services/snackbar.service';
import { DialogService } from '../../services/dialog.service';

@Component({
  selector: 'app-authorization-engines',
  templateUrl: './authorization-engines.component.html',
  styleUrls: ['./authorization-engines.component.scss'],
  standalone: false,
})
export class DomainSettingsAuthorizationEnginesComponent implements OnInit {
  authorizationEngines: any[];
  private authorizationEnginePlugins: Record<string, any> = {};
  domainId: string;

  constructor(
    private authorizationEngineService: AuthorizationEngineService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.authorizationEngines = this.route.snapshot.data['authorizationEngines'];
    this.authorizationEnginePlugins = this.toPluginMap(this.route.snapshot.data['authorizationEnginePlugins']);
  }

  loadAuthorizationEngines() {
    this.authorizationEngineService.findByDomain(this.domainId).subscribe((response) => (this.authorizationEngines = response));
  }

  get isEmpty() {
    return !this.authorizationEngines || this.authorizationEngines.length === 0;
  }

  getAuthorizationEnginePlugin(type: any) {
    return this.authorizationEnginePlugins?.[type] ?? null;
  }

  getAuthorizationEngineTypeIcon(type: any) {
    const plugin = this.getAuthorizationEnginePlugin(type);
    if (plugin?.icon) {
      const name = plugin.displayName ? plugin.displayName : plugin.name;
      return `<img width="24" height="24" src="${plugin.icon}" alt="${name} image" title="${name}"/>`;
    }
    return `<span class="material-icons">gavel</span>`;
  }

  displayType(type: any) {
    const plugin = this.getAuthorizationEnginePlugin(type);
    if (plugin) {
      return plugin.displayName ? plugin.displayName : plugin.name;
    }
    return 'Custom';
  }

  delete(id: any, event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Authorization Engine', 'Are you sure you want to delete this authorization engine?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationEngineService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Authorization engine deleted');
          this.loadAuthorizationEngines();
        }),
      )
      .subscribe();
  }

  private toPluginMap(plugins: any[]): Record<string, any> {
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
      {} as Record<string, any>,
    );
  }
}
