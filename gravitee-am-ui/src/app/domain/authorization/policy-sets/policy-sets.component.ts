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

import { PolicySetService } from '../../../services/policy-set.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-policy-sets',
  templateUrl: './policy-sets.component.html',
  styleUrls: ['./policy-sets.component.scss'],
  standalone: false,
})
export class PolicySetsComponent implements OnInit {
  policySets: any[];
  domainId: string;

  constructor(
    private policySetService: PolicySetService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.policySets = this.route.snapshot.data['policySets'] || [];
  }

  loadPolicySets() {
    this.policySetService.findByDomain(this.domainId).subscribe((policySets) => (this.policySets = policySets));
  }

  get isEmpty() {
    return !this.policySets || this.policySets.length === 0;
  }

  delete(id: string, event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Policy Set', 'Are you sure you want to delete this policy set?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.policySetService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Policy set deleted');
          this.loadPolicySets();
        }),
      )
      .subscribe();
  }
}
