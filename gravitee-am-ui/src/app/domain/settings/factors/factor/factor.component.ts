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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {OrganizationService} from '../../../../services/organization.service';
import {SnackbarService} from '../../../../services/snackbar.service';
import {DialogService} from '../../../../services/dialog.service';
import {AuthService} from '../../../../services/auth.service';
import {FactorService} from '../../../../services/factor.service';

@Component({
  selector: 'app-factor',
  templateUrl: './factor.component.html',
  styleUrls: ['./factor.component.scss']
})
export class FactorComponent implements OnInit {
  private domainId: string;
  formChanged = false;
  configurationIsValid = true;
  configurationPristine = true;
  factor: any;
  factorSchema: any;
  factorConfiguration: any;
  updateFactorConfiguration: any;
  editMode: boolean;
  private factorPlugins: any[];
  private resourcePlugins: any[];
  private resources: any[];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private organizationService: OrganizationService,
              private factorService: FactorService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.factor = this.route.snapshot.data['factor'];
    this.factorConfiguration = JSON.parse(this.factor.configuration);
    this.updateFactorConfiguration = this.factorConfiguration;
    this.editMode = this.authService.hasPermissions(['domain_factor_update']);
    this.factorPlugins = this.route.snapshot.data['factorPlugins'];
    this.resourcePlugins = this.route.snapshot.data['resourcePlugins'];
    this.resources = this.route.snapshot.data['resources'];

    this.organizationService.factorSchema(this.factor.type).subscribe(data => {
      this.factorSchema = data;
      // set the grant_type value

      if (this.factorSchema.properties.factorType) {
        this.factor.factorType = this.factorSchema.properties.factorType.default;
      }

      if (this.factorSchema.properties.graviteeResource && this.resources && this.resources.length > 0) {
        const resourcePluginTypeToCategories = this.resourcePlugins.reduce((accumulator, currentPlugin) => ({ ...accumulator, [currentPlugin.id]: currentPlugin.categories}), {});
        const factorPluginTypeToCategories = this.factorPlugins.reduce((accumulator, currentPlugin) => ({ ...accumulator, [currentPlugin.id]: currentPlugin.category}), {});
        const factorCategory = factorPluginTypeToCategories[this.factor.type];
        // filter resources with category compatible with the Factor Plugin one
        const filteredResources = this.resources.filter(r =>
          resourcePluginTypeToCategories[r.type].filter(resourceCategory => resourceCategory === factorCategory).length > 0
        );

        this.factorSchema.properties.graviteeResource['x-schema-form'] = { 'type' : 'select' };
        if (filteredResources.length > 0) {
          this.factorSchema.properties.graviteeResource.enum = filteredResources.map(r => r.id);
          this.factorSchema.properties.graviteeResource['x-schema-form'].titleMap = filteredResources.reduce(function(map, obj) {
            map[obj.id] = obj.name;
            return map;
          }, {});
        } else {
          // if list of resources is empty, disable the field
          this.factorSchema.properties.graviteeResource['readonly'] = true;
        }
      }

    });
  }

  update() {
    this.factor.configuration = JSON.stringify(this.updateFactorConfiguration);
    this.factorService.update(this.domainId, this.factor.id, this.factor).subscribe(data => {
      this.snackbarService.open('Factor updated');
    })
  }

  enableFactorUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.factor.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateFactorConfiguration = configurationWrapper.configuration;
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Factor', 'Are you sure you want to delete this factor ?')
      .subscribe(res => {
        if (res) {
          this.factorService.delete(this.domainId, this.factor.id).subscribe(() => {
            this.snackbarService.open('Factor deleted');
            this.router.navigate(['..'], { relativeTo: this.route });
          });
        }
      });
  }

  isFido2Factor(){
    return this.factor.type.includes("fido2-am-factor");
  }
}
