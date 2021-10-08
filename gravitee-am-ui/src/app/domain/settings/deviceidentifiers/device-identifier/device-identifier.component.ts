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
import {DeviceIdentifierService} from "../../../../services/device-identifier.service";

@Component({
  selector: 'app-device-identifier',
  templateUrl: './device-identifier.component.html',
  styleUrls: ['./device-identifier.component.scss']
})
export class DeviceIdentifierComponent implements OnInit {
  private domainId: string;
  formChanged = false;
  configurationIsValid = true;
  configurationPristine = true;
  deviceIdentifier: any;
  deviceIdentifierSchema: any;
  deviceIdentifierConfiguration: any;
  updateDeviceIdentifierConfiguration: any;
  editMode: boolean;

  private deviceIdentifierTypes: any = {
    'fingerprintjs-v3-community-device-identifier': 'FingerprintJS v3 community',
    'fingerprintjs-v3-pro-device-identifier': 'FingerprintJS v3 Pro'
  };

  constructor(private route: ActivatedRoute,
              private router: Router,
              private organizationService: OrganizationService,
              private deviceIdentifierService: DeviceIdentifierService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.deviceIdentifier = this.route.snapshot.data['deviceIdentifier'];
    this.deviceIdentifierConfiguration = JSON.parse(this.deviceIdentifier.configuration);
    this.updateDeviceIdentifierConfiguration = this.deviceIdentifierConfiguration;
    this.editMode = this.authService.hasPermissions(['domain_device_identifier_update']);

    this.organizationService.deviceIdentifiersSchema(this.deviceIdentifier.type).subscribe(data => {
      this.deviceIdentifierSchema = data;
    });
  }

  update() {
    this.deviceIdentifier.configuration = JSON.stringify(this.updateDeviceIdentifierConfiguration);
    this.deviceIdentifierService.update(this.domainId, this.deviceIdentifier.id, this.deviceIdentifier).subscribe(data => {
      this.snackbarService.open('Device identifier updated');
    })
  }

  enableDeviceIdentifierDetectionUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.deviceIdentifier.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateDeviceIdentifierConfiguration = configurationWrapper.configuration;
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete device identifier', 'Are you sure you want to delete this Device Identifier ?')
      .subscribe(res => {
        if (res) {
          this.deviceIdentifierService.delete(this.domainId, this.deviceIdentifier.id).subscribe(() => {
            this.snackbarService.open('Device identifier deleted');
            this.router.navigate(['..'], { relativeTo: this.route });
          });
        }
      });
  }

  displayType(type) {
    if (this.deviceIdentifierTypes[type]) {
      return this.deviceIdentifierTypes[type];
    }
    return type;
  }
}
