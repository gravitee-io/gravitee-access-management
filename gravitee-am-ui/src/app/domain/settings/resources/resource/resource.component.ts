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
import {ResourceService} from '../../../../services/resource.service';

@Component({
  selector: 'app-resource',
  templateUrl: './resource.component.html',
  styleUrls: ['./resource.component.scss']
})
export class ResourceComponent implements OnInit {
  private domainId: string;
  formChanged = false;
  configurationIsValid = true;
  configurationPristine = true;
  resource: any;
  resourceSchema: any;
  resourceConfiguration: any;
  updateResourceConfiguration: any;
  editMode: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private organizationService: OrganizationService,
              private resourceService: ResourceService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.resource = this.route.snapshot.data['resource'];
    this.resourceConfiguration = JSON.parse(this.resource.configuration);
    this.updateResourceConfiguration = this.resourceConfiguration;
    this.editMode = this.authService.hasPermissions(['domain_resource_update']);
    this.organizationService.resourceSchema(this.resource.type).subscribe(data => {
      this.resourceSchema = data;
    });
  }

  update() {
    this.resource.configuration = JSON.stringify(this.updateResourceConfiguration);
    this.resourceService.update(this.domainId, this.resource.id, this.resource).subscribe(data => {
      this.snackbarService.open('Resource updated');
    })
  }

  enableResourceUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.resource.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateResourceConfiguration = configurationWrapper.configuration;
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Resource', 'Are you sure you want to delete this resource ?')
      .subscribe(res => {
        if (res) {
          this.resourceService.delete(this.domainId, this.resource.id).subscribe(() => {
            this.snackbarService.open('Resource deleted');
            this.router.navigate(['..'], { relativeTo: this.route });
          }, () => {
            this.router.navigate(['..', this.resource.id], { relativeTo: this.route });
          });
        }
      });
  }
}
