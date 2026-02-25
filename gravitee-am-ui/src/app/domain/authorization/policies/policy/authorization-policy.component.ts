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

import { AuthorizationPolicyService } from '../../../../services/authorization-policy.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DialogService } from '../../../../services/dialog.service';
import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-authorization-policy',
  templateUrl: './authorization-policy.component.html',
  styleUrls: ['./authorization-policy.component.scss'],
  standalone: false,
})
export class AuthorizationPolicyComponent implements OnInit {
  policy: any;
  versions: any[] = [];
  domainId: string;
  formChanged = false;
  editMode: boolean;
  updateComment = '';

  constructor(
    private authorizationPolicyService: AuthorizationPolicyService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.policy = this.route.snapshot.data['policy'];
    this.editMode = this.authService.hasPermissions(['domain_authorization_policy_update']);
    this.loadVersions();
  }

  loadVersions() {
    this.authorizationPolicyService.getVersions(this.domainId, this.policy.id).subscribe((versions) => {
      this.versions = versions;
    });
  }

  update() {
    const updatePayload = {
      name: this.policy.name,
      description: this.policy.description,
      content: this.policy.content,
      comment: this.updateComment || undefined,
    };
    this.authorizationPolicyService.update(this.domainId, this.policy.id, updatePayload).subscribe((data) => {
      this.policy = data;
      this.formChanged = false;
      this.updateComment = '';
      this.snackbarService.open('Authorization policy updated');
      this.loadVersions();
    });
  }

  delete(event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Policy', 'Are you sure you want to delete this authorization policy?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationPolicyService.delete(this.domainId, this.policy.id)),
        tap(() => {
          this.snackbarService.open('Authorization policy deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  rollback(version: number) {
    this.dialogService
      .confirm('Rollback Policy', 'Are you sure you want to rollback to version ' + version + '?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationPolicyService.rollback(this.domainId, this.policy.id, version)),
        tap((data) => {
          this.policy = data;
          this.snackbarService.open('Policy rolled back to version ' + version);
          this.loadVersions();
        }),
      )
      .subscribe();
  }
}
