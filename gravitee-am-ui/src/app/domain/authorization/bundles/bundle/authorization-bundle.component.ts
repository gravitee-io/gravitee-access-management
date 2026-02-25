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
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthorizationBundleService } from '../../../../services/authorization-bundle.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DialogService } from '../../../../services/dialog.service';
import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-authorization-bundle',
  templateUrl: './authorization-bundle.component.html',
  styleUrls: ['./authorization-bundle.component.scss'],
  standalone: false,
})
export class AuthorizationBundleComponent implements OnInit {
  bundle: any;
  versions: any[] = [];
  domainId: string;
  formChanged = false;
  editMode: boolean;
  updateComment = '';

  constructor(
    private authorizationBundleService: AuthorizationBundleService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.bundle = this.route.snapshot.data['bundle'];
    this.editMode = this.authService.hasPermissions(['domain_authorization_bundle_update']);
    this.loadVersions();
  }

  loadVersions() {
    this.authorizationBundleService.getVersions(this.domainId, this.bundle.id).subscribe((versions) => {
      this.versions = versions;
    });
  }

  update() {
    const updatePayload = {
      name: this.bundle.name,
      description: this.bundle.description,
      policies: this.bundle.policies,
      schema: this.bundle.schema,
      entities: this.bundle.entities,
      comment: this.updateComment || undefined,
    };
    this.authorizationBundleService.update(this.domainId, this.bundle.id, updatePayload).subscribe((data) => {
      this.bundle = data;
      this.formChanged = false;
      this.updateComment = '';
      this.snackbarService.open('Authorization bundle updated');
      this.loadVersions();
    });
  }

  delete(event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Bundle', 'Are you sure you want to delete this authorization bundle?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationBundleService.delete(this.domainId, this.bundle.id)),
        tap(() => {
          this.snackbarService.open('Authorization bundle deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  rollback(version: number) {
    this.dialogService
      .confirm('Rollback Bundle', 'Are you sure you want to rollback to version ' + version + '?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationBundleService.rollback(this.domainId, this.bundle.id, version)),
        tap((data) => {
          this.bundle = data;
          this.snackbarService.open('Bundle rolled back to version ' + version);
          this.loadVersions();
        }),
      )
      .subscribe();
  }
}
