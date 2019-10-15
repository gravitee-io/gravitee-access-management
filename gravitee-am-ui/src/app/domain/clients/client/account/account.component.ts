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
import { Component } from '@angular/core';
import {ActivatedRoute} from "@angular/router";
import {ClientService} from "../../../../services/client.service";
import {SnackbarService} from "../../../../services/snackbar.service";

@Component({
  selector: 'app-client-account-settings',
  templateUrl: './account.component.html',
  styleUrls: ['./account.component.scss']
})
export class ClientAccountSettingsComponent {
  private domainId: string;
  client: any;
  accountSettings: any;

  constructor(private route: ActivatedRoute,
              private clientService: ClientService,
              private snackbarService: SnackbarService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.client = this.route.snapshot.parent.data['client'];
    this.accountSettings = this.client.accountSettings || { 'inherited' : true };
  }

  updateAccountSettings(accountSettings) {
    this.accountSettings = accountSettings;
    this.clientService.patchAccountSettings(this.domainId, this.client.id, accountSettings).subscribe(data => {
      this.client = data;
      this.route.snapshot.parent.data['client'] = this.client;
      this.snackbarService.open("User Accounts Settings updated");
    });
  }
}
