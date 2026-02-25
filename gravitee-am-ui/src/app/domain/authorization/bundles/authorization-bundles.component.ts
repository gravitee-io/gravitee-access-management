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

import { AuthorizationBundleService } from '../../../services/authorization-bundle.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-authorization-bundles',
  templateUrl: './authorization-bundles.component.html',
  styleUrls: ['./authorization-bundles.component.scss'],
  standalone: false,
})
export class AuthorizationBundlesComponent implements OnInit {
  bundles: any[];
  domainId: string;

  constructor(
    private authorizationBundleService: AuthorizationBundleService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.bundles = this.route.snapshot.data['bundles'] || [];
  }

  loadBundles() {
    this.authorizationBundleService.findByDomain(this.domainId).subscribe((bundles) => (this.bundles = bundles));
  }

  get isEmpty() {
    return !this.bundles || this.bundles.length === 0;
  }

  delete(id: string, event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Bundle', 'Are you sure you want to delete this authorization bundle?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationBundleService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Authorization bundle deleted');
          this.loadBundles();
        }),
      )
      .subscribe();
  }
}
