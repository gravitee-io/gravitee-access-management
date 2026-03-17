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
import { PolicySetService } from '../../../../services/policy-set.service';
import { EntityStoreService } from '../../../../services/entity-store.service';
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
  domainId: string;
  formChanged = false;
  editMode: boolean;

  policySets: any[] = [];
  entityStores: any[] = [];

  policySetVersionsMap: Record<string, any[]> = {};
  entityStoreVersionsMap: Record<string, any[]> = {};

  constructor(
    private authorizationBundleService: AuthorizationBundleService,
    private policySetService: PolicySetService,
    private entityStoreService: EntityStoreService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.bundle = this.route.snapshot.data['bundle'];
    // Ensure lists exist
    if (!this.bundle.policySets) {
      this.bundle.policySets = [];
    }
    if (!this.bundle.entityStores) {
      this.bundle.entityStores = [];
    }
    this.editMode = this.authService.hasPermissions(['domain_authorization_bundle_update']);
    this.loadComponents();
  }

  loadComponents() {
    this.policySetService.findByDomain(this.domainId).subscribe((ps) => {
      this.policySets = ps;
      // Load versions for existing selections
      for (const ref of this.bundle.policySets) {
        if (ref.id) {
          this.loadPolicySetVersions(ref.id);
        }
      }
    });
    this.entityStoreService.findByDomain(this.domainId).subscribe((es) => {
      this.entityStores = es;
      for (const ref of this.bundle.entityStores) {
        if (ref.id) {
          this.loadEntityStoreVersions(ref.id);
        }
      }
    });
  }

  loadPolicySetVersions(policySetId: string) {
    this.policySetService.getVersions(this.domainId, policySetId).subscribe((v) => {
      this.policySetVersionsMap[policySetId] = v.sort((a: any, b: any) => b.version - a.version);
    });
  }

  loadEntityStoreVersions(entityStoreId: string) {
    this.entityStoreService.getVersions(this.domainId, entityStoreId).subscribe((v) => {
      this.entityStoreVersionsMap[entityStoreId] = v.sort((a: any, b: any) => b.version - a.version);
    });
  }

  addPolicySet() {
    this.bundle.policySets.push({ id: '', version: 0, pinToLatest: true });
    this.formChanged = true;
  }

  removePolicySet(index: number) {
    this.bundle.policySets.splice(index, 1);
    this.formChanged = true;
  }

  onPolicySetSelected(index: number, policySetId: string) {
    const entry = this.bundle.policySets[index];
    entry.id = policySetId;
    if (policySetId) {
      const ps = this.policySets.find((p) => p.id === policySetId);
      if (ps) {
        entry.version = ps.latestVersion;
      }
      this.loadPolicySetVersions(policySetId);
    }
    this.formChanged = true;
  }

  addEntityStore() {
    this.bundle.entityStores.push({ id: '', version: 0, pinToLatest: true });
    this.formChanged = true;
  }

  removeEntityStore(index: number) {
    this.bundle.entityStores.splice(index, 1);
    this.formChanged = true;
  }

  onEntityStoreSelected(index: number, entityStoreId: string) {
    const entry = this.bundle.entityStores[index];
    entry.id = entityStoreId;
    if (entityStoreId) {
      const es = this.entityStores.find((e) => e.id === entityStoreId);
      if (es) {
        entry.version = es.latestVersion;
      }
      this.loadEntityStoreVersions(entityStoreId);
    }
    this.formChanged = true;
  }

  onPinToLatestChange() {
    this.formChanged = true;
  }

  update() {
    const updatePayload = {
      name: this.bundle.name,
      description: this.bundle.description,
      policySets: this.bundle.policySets,
      entityStores: this.bundle.entityStores,
    };
    this.authorizationBundleService.update(this.domainId, this.bundle.id, updatePayload).subscribe((data) => {
      this.bundle = data;
      if (!this.bundle.policySets) {
        this.bundle.policySets = [];
      }
      if (!this.bundle.entityStores) {
        this.bundle.entityStores = [];
      }
      this.formChanged = false;
      this.snackbarService.open('Authorization bundle updated');
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
}
