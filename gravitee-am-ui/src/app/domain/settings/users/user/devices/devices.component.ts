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
import { ActivatedRoute, Router } from "@angular/router";
import { SnackbarService } from "../../../../../services/snackbar.service";
import { DialogService } from "../../../../../services/dialog.service";
import { UserService } from "../../../../../services/user.service";
import * as _ from 'lodash';
import {AuthService} from "../../../../../services/auth.service";
import {DeviceIdentifiersResolver} from "../../../../../resolvers/device-identifiers.resolver";

@Component({
  selector: 'app-user-devices',
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss']
})
export class UserDevicesComponent implements OnInit {
  private domainId: string;
  private user: any;
  private consents: any[];
  canDelete: boolean;
  devices: any[];
  deviceIdentifiers: any[];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private userService: UserService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.user = this.route.snapshot.data['user'];
    this.deviceIdentifiers = this.route.snapshot.data['deviceIdentifiers'];
    this.devices = this.route.snapshot.data['devices'];
    this.consents =  this.route.snapshot.data['consents'];
    this.canDelete = this.authService.hasPermissions(['domain_user_device_delete']);
  }

  getApplicationName(appId){
    const consent = this.consents.find(consent => consent.clientId === appId);
    if (consent){
      return consent.clientEntity.name;
    }
    return appId;
  }

  getDeviceIdentifierName(id){
    const deviceIdentifier = this.deviceIdentifiers.find(deviceIdentifier => deviceIdentifier.id === id);
    if (id && deviceIdentifier){
      return deviceIdentifier.name;
    }
    return id;
  }

  remove($event, device) {
    $event.preventDefault();
    if(this.canDelete){
      this.dialogService
        .confirm('Remove trusted device', 'Are you sure you want to remove this trusted device ?')
        .subscribe(res => {
          if (res) {
            this.userService.removeDevice(this.domainId, this.user.id, device.id).subscribe(response => {
              this.snackbarService.open('Device has been deleted deleted');
              const index = this.devices.indexOf(device);
              if (index > -1) {
                this.devices.splice(index, 1);
              }
            });
          }
        });
    }
  }
}
