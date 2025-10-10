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
import { Component, OnInit, Input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'authorization-engine-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss'],
  standalone: false,
})
export class AuthorizationEngineCreationStep1Component implements OnInit {
  authorizationEnginePlugins: any[];
  existingEngines: any[];
  @Input() authorizationEngine;
  filter: string;
  filteredPlugins: any[];
  hasAvailablePlugins: boolean;

  constructor(private route: ActivatedRoute) {}

  ngOnInit() {
    this.filter = '';
    this.authorizationEnginePlugins = this.route.snapshot.data['authorizationEnginePlugins'];
    this.existingEngines = this.route.snapshot.data['authorizationEngines'] || [];
    this.filteredPlugins = this.getFilteredPlugins();
  }

  selectEngineType(plugin: any) {
    // Don't allow selection if type is already in use
    if (this.isTypeInUse(plugin.id)) {
      return;
    }
    this.authorizationEngine.type = plugin.id;
  }

  displayName(plugin) {
    return plugin.displayName ? plugin.displayName : plugin.name;
  }

  getIcon(plugin) {
    if (plugin?.icon) {
      const title = plugin.displayName ? plugin.displayName : plugin.name;
      return `<img mat-card-avatar src="${plugin.icon}" alt="${title} image" title="${title}"/>`;
    }
    return `<i class="material-icons">gavel</i>`;
  }

  isTypeInUse(type: string): boolean {
    return this.existingEngines.some((engine) => engine.type === type);
  }

  private getFilteredPlugins() {
    const plugins = Object.values(this.authorizationEnginePlugins).filter((plugin: any) => plugin.deployed !== false);
    if (this.filter != null && this.filter.trim().length > 0) {
      return plugins.filter((plugin: any) => {
        let fields = [plugin.name.toLowerCase()];
        if (plugin.displayName != null) {
          fields.push(plugin.displayName.toLowerCase());
        }
        if (plugin.labels != null) {
          fields = [...fields, ...plugin.labels.map((t) => t.toLowerCase())];
        }
        return JSON.stringify(fields).includes(this.filter.toLowerCase());
      });
    }
    this.hasAvailablePlugins = plugins.length > 0;
    return plugins;
  }

  onFilterChange() {
    this.filteredPlugins = this.getFilteredPlugins();
  }
}
