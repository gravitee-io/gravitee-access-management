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

import { AuthorizationPolicyService } from '../../../services/authorization-policy.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-authorization-policies',
  templateUrl: './authorization-policies.component.html',
  styleUrls: ['./authorization-policies.component.scss'],
  standalone: false,
})
export class AuthorizationPoliciesComponent implements OnInit {
  policies: any[];
  domainId: string;

  constructor(
    private authorizationPolicyService: AuthorizationPolicyService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.policies = this.route.snapshot.data['policies'] || [];
  }

  loadPolicies() {
    this.authorizationPolicyService.findByDomain(this.domainId).subscribe((policies) => (this.policies = policies));
  }

  get isEmpty() {
    return !this.policies || this.policies.length === 0;
  }

  delete(id: string, event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Policy', 'Are you sure you want to delete this authorization policy?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationPolicyService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Authorization policy deleted');
          this.loadPolicies();
        }),
      )
      .subscribe();
  }
}
