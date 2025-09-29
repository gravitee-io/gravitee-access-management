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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs/operators';

import { OrganizationService } from '../../../../../../services/organization.service';
import { SnackbarService } from '../../../../../../services/snackbar.service';
import { enrichFormWithCerts } from '../../../provider/provider.form.enricher';

@Component({
  selector: 'provider-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
})
export class ProviderCreationStep2Component implements OnInit, OnChanges {
  @Input() provider: any;
  @Input() configurationIsValid: boolean;
  @Output() configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  configuration: any;
  providerSchema: any = {};
  domainWhitelistPattern = '';
  private certificates: any[];
  private datasources: any[];

  constructor(
    private organizationService: OrganizationService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.certificates = this.route.snapshot.data['certificates'];
    this.datasources = this.route.snapshot.data['datasources'];
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.provider) {
      this.organizationService
        .identitySchema(changes.provider.currentValue.type)
        .pipe(map((schema) => enrichFormWithCerts(schema, this.certificates)))
        .subscribe((data) => {
          // Process datasource widgets BEFORE setting the schema
          this.applyDataSourceSelection(data);
          this.providerSchema = data;
        });
    }
  }

  enableProviderCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.provider.configuration = configurationWrapper.configuration;
  }

  addDomainWhitelistPattern(event) {
    event.preventDefault();
    if (this.domainWhitelistPattern) {
      if (!this.provider.domainWhitelist.some((el) => el === this.domainWhitelistPattern)) {
        this.provider.domainWhitelist.push(this.domainWhitelistPattern);
        this.provider.domainWhitelist = [...this.provider.domainWhitelist];
        this.domainWhitelistPattern = '';
      } else {
        this.snackbarService.open(`Error : domain whitelist pattern "${this.domainWhitelistPattern}" already exists`);
      }
    }
  }

  removeDomainWhitelistPattern(dwPattern) {
    const index = this.provider.domainWhitelist.indexOf(dwPattern);
    if (index > -1) {
      this.provider.domainWhitelist.splice(index, 1);
    }
  }

  private applyDataSourceSelection(schema: any): void {
    if (schema && schema.properties) {
      for (const key in schema.properties) {
        const property = schema.properties[key];
        this.applyDataSourceSelectionRecursive(property, key);
      }
    }
  }

  private applyDataSourceSelectionRecursive(property: any, propertyName?: string): void {
    // Handle nested objects
    if (property.type === 'object' && property.properties) {
      for (const key in property.properties) {
        const child = property.properties[key];
        this.applyDataSourceSelectionRecursive(child, key);
      }
    }

    // Handle arrays
    if (property.type === 'array') {
      if (property.items?.properties) {
        for (const key in property.items.properties) {
          const child = property.items.properties[key];
          this.applyDataSourceSelectionRecursive(child, key);
        }
      }
    }

    // Apply datasource widget transformation
    this.applyDataSourceWidget(property, propertyName);
  }

  private applyDataSourceWidget(property: any, propertyName?: string): void {
    if ('datasource' === property.widget || 'datasource' === propertyName) {
      if (this.datasources?.length > 0) {
        property['x-schema-form'] = { type: 'select' };
        property.enum = this.datasources.map((d) => d.id);
        property['x-schema-form'].titleMap = this.datasources.reduce((map, obj) => {
          map[obj.id] = obj.name;
          return map;
        }, {});
      } else {
        // if list of datasources is empty, disable the field
        property['readonly'] = true;
      }
    }
  }
}
