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
import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {OrganizationService} from '../../../../../../services/organization.service';

@Component({
  selector: 'factor-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss']
})
export class FactorCreationStep2Component implements OnInit {
  @Input('factor') factor: any;
  @Input('configurationIsValid') configurationIsValid: boolean;
  @Output('configurationIsValidChange') configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  formChanged = false;
  configuration: any;
  factorSchema: any = {};
  private factorPlugins: any[];
  private resourcePlugins: any[];
  private resources: any[];

  constructor(
    private organizationService: OrganizationService,
    private route: ActivatedRoute) { }

  ngOnInit() {
    this.factorPlugins = this.route.snapshot.data['factorPlugins'];
    this.resourcePlugins = this.route.snapshot.data['resourcePlugins'];
    this.resources = this.route.snapshot.data['resources'];

    this.organizationService.factorSchema(this.factor.type).subscribe(data => {
      this.factorSchema = data;
      if (this.factorSchema.properties.factorType) {
        this.factor.factorType = this.factorSchema.properties.factorType.default;
      }
      for (const key in this.factorSchema.properties) {
        const property = this.factorSchema.properties[key];
        this.applyResourceSelection(property, key);
      }
    });
  }

  enableFactorCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.factor.configuration = configurationWrapper.configuration;
  }

  private applyResourceSelection(property, propertyName?) {
    if (property.type === 'array') {
      if (property.items && property.items.properties) {
        for (const key in property.items.properties) {
          const child = property.items.properties[key];
          this.applyResourceSelection(child);
        }
      }
    }

    if ('graviteeResource' === property.widget || 'graviteeResource' === propertyName) {
      if (this.resources && this.resources.length > 0) {
        const resourcePluginTypeToCategories = this.resourcePlugins.reduce((accumulator, currentPlugin) => ({ ...accumulator, [currentPlugin.id]: currentPlugin.categories}), {});
        const factorPluginTypeToCategories = this.factorPlugins.reduce((accumulator, currentPlugin) => ({ ...accumulator, [currentPlugin.id]: currentPlugin.category}), {});
        const factorCategory = factorPluginTypeToCategories[this.factor.type];
        // filter resources with category compatible with the Factor Plugin one
        const filteredResources = this.resources.filter(r =>
          factorCategory === 'any' ||
          (resourcePluginTypeToCategories[r.type] && resourcePluginTypeToCategories[r.type].filter(resourceCategory => resourceCategory === factorCategory).length > 0)
        );

        property['x-schema-form'] = { 'type' : 'select' };
        if (filteredResources.length > 0) {
          property.enum = filteredResources.map(r => r.id);
          property['x-schema-form'].titleMap = filteredResources.reduce(function(map, obj) {
            map[obj.id] = obj.name;
            return map;
          }, {});
        } else {
          // if list of resources is empty, disable the field
          property['readonly'] = true;
        }
      } else {
        // if list of resources is empty, disable the field
        property['readonly'] = true;
      }
    }
  }
}
