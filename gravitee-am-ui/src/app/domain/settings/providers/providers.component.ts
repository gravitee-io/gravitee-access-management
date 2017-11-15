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
import { ProviderService } from "../../shared/services/provider.service";
import { SnackbarService } from "../../../core/services/snackbar.service";
import { DialogService } from "../../../core/services/dialog.service";
import { ActivatedRoute } from "@angular/router";

@Component({
  selector: 'app-providers',
  templateUrl: './providers.component.html',
  styleUrls: ['./providers.component.scss']
})
export class DomainSettingsProvidersComponent implements OnInit {
  private providers: any[];
  domainId: string;

  constructor(private providerService: ProviderService, private dialogService: DialogService,
              private snackbarService: SnackbarService, private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.providers = this.route.snapshot.data['providers'];
  }

  loadProviders() {
    this.providerService.findByDomain(this.domainId).subscribe(response => this.providers = response.json());
  }

  get isEmpty() {
    return !this.providers || this.providers.length == 0;
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Provider', 'Are you sure you want to delete this provider ?')
      .subscribe(res => {
        if (res) {
          this.providerService.delete(this.domainId, id).subscribe(response => {
            this.snackbarService.open("Provider deleted");
            this.loadProviders();
          });
        }
      });
  }

}
