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
import { ProviderService } from "../../../services/provider.service";
import { SnackbarService } from "../../../services/snackbar.service";
import { DialogService } from "../../../services/dialog.service";
import { ActivatedRoute } from "@angular/router";
import { ScopeService } from "../../../services/scope.service";

@Component({
  selector: 'app-scopes',
  templateUrl: './scopes.component.html',
  styleUrls: ['./scopes.component.scss']
})
export class DomainSettingsScopesComponent implements OnInit {
  private scopes: any[];
  domainId: string;

  constructor(private scopeService: ScopeService, private dialogService: DialogService,
              private snackbarService: SnackbarService, private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.scopes = this.route.snapshot.data['scopes'];
  }

  loadScopes() {
    this.scopeService.findByDomain(this.domainId).subscribe(response => this.scopes = response.json());
  }

  get isEmpty() {
    return !this.scopes || this.scopes.length == 0;
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Scope', 'Are you sure you want to delete this scope ?')
      .subscribe(res => {
        if (res) {
          this.scopeService.delete(this.domainId, id).subscribe(response => {
            this.snackbarService.open("Scope deleted");
            this.loadScopes();
          });
        }
      });
  }

}
