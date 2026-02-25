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
import { ActivatedRoute } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthorizationDataService } from '../../../services/authorization-data.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-authorization-data',
  templateUrl: './authorization-data.component.html',
  styleUrls: ['./authorization-data.component.scss'],
  standalone: false,
})
export class AuthorizationDataComponent implements OnInit {
  data: any;
  versions: any[] = [];
  domainId: string;
  formChanged = false;
  editMode: boolean;
  updateComment = '';

  constructor(
    private authorizationDataService: AuthorizationDataService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.data = this.route.snapshot.data['authorizationData'];
    this.editMode = this.authService.hasPermissions(['domain_authorization_data_update']);
    if (this.data) {
      this.loadVersions();
    } else {
      // Initialise empty data for creation
      this.data = { engineType: 'cedar', content: '' };
    }
  }

  get isNew() {
    return !this.data?.id;
  }

  loadVersions() {
    if (!this.data?.id) return;
    this.authorizationDataService.getVersions(this.domainId).subscribe(
      (versions) => (this.versions = versions),
      () => {}, // Silently handle if no versions yet
    );
  }

  save() {
    const payload = {
      engineType: this.data.engineType,
      content: this.data.content,
      comment: this.updateComment || undefined,
    };
    this.authorizationDataService.update(this.domainId, payload).subscribe((result) => {
      this.data = result;
      this.formChanged = false;
      this.updateComment = '';
      this.snackbarService.open(this.isNew ? 'Authorization data created' : 'Authorization data updated');
      this.loadVersions();
    });
  }

  rollback(version: number) {
    this.dialogService
      .confirm('Rollback Data', 'Are you sure you want to rollback to version ' + version + '?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationDataService.rollback(this.domainId, version)),
        tap((result) => {
          this.data = result;
          this.snackbarService.open('Data rolled back to version ' + version);
          this.loadVersions();
        }),
      )
      .subscribe();
  }
}
