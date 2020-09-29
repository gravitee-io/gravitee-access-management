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
import {Component, OnInit, ViewChild} from '@angular/core';
import {NgForm} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {ScopeService} from '../../../../services/scope.service';
import {SnackbarService} from '../../../../services/snackbar.service';
import {DialogService} from '../../../../services/dialog.service';
import {AuthService} from '../../../../services/auth.service';
import * as moment from 'moment';

@Component({
  selector: 'app-scope',
  templateUrl: './scope.component.html',
  styleUrls: ['./scope.component.scss']
})
export class ScopeComponent implements OnInit {
  private domainId: string;
  scope: any;
  formChanged = false;
  expiresIn: any;
  unitTime: any;
  editMode: boolean;
  deleteMode: boolean;
  @ViewChild('scopeForm') public scopeForm: NgForm;

  constructor(private scopeService: ScopeService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.scope = this.route.snapshot.data['scope'];
    this.editMode = this.authService.hasPermissions(['domain_scope_update']);
    this.deleteMode = this.authService.hasPermissions(['domain_scope_delete']);
  }

  update() {
    // Force lowercase for scope key
    this.scope.key = this.scope.key.toLowerCase();
    // set duration time for user consent
    if (this.expiresIn && this.unitTime) {
      this.scope.expiresIn = moment.duration(this.expiresIn, this.unitTime).asSeconds();
    }
    this.scopeService.update(this.domainId, this.scope.id, this.scope).subscribe(data => {
      this.scope = data;
      this.formChanged = false;
      this.scopeForm.reset(this.scope);
      this.expiresIn = null;
      this.unitTime = null;
      this.snackbarService.open('Scope updated');
    });
  }

  formIsInvalid() {
    return (this.expiresIn > 0 && !this.unitTime) || (this.expiresIn <= 0 && this.unitTime);
  }

  getScopeExpiry() {
    return (this.scope.expiresIn) ? moment.duration(this.scope.expiresIn, 'seconds').humanize() : 'no time set';
  }

  clearExpiry() {
    this.scope.expiresIn = null;
    this.formChanged = true;
  }

  enableScopeDiscovery(event) {
    this.scope.discovery = event.checked;
    this.formChanged = true;
  }

  isDiscovery() {
    return this.scope.discovery;
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Scope', 'Are you sure you want to delete this scope ?')
      .subscribe(res => {
        if (res) {
          this.scopeService.delete(this.domainId, this.scope.id).subscribe(() => {
            this.snackbarService.open('Scope deleted');
            this.router.navigate(['..'], { relativeTo: this.route });
          });
        }
      });
  }
}
