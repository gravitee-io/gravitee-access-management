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
import { AuthorizationSchemaService } from '../../../../services/authorization-schema.service';
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
  schemas: any[] = [];
  entityStores: any[] = [];

  policySetVersions: any[] = [];
  schemaVersions: any[] = [];
  entityStoreVersions: any[] = [];

  constructor(
    private authorizationBundleService: AuthorizationBundleService,
    private policySetService: PolicySetService,
    private authorizationSchemaService: AuthorizationSchemaService,
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
    this.editMode = this.authService.hasPermissions(['domain_authorization_bundle_update']);
    this.loadComponents();
  }

  loadComponents() {
    this.policySetService.findByDomain(this.domainId).subscribe((ps) => {
      this.policySets = ps;
      if (this.bundle.policySetId) {
        this.loadPolicySetVersions(this.bundle.policySetId);
      }
    });
    this.authorizationSchemaService.findByDomain(this.domainId).subscribe((s) => {
      this.schemas = s;
      if (this.bundle.schemaId) {
        this.loadSchemaVersions(this.bundle.schemaId);
      }
    });
    this.entityStoreService.findByDomain(this.domainId).subscribe((es) => {
      this.entityStores = es;
      if (this.bundle.entityStoreId) {
        this.loadEntityStoreVersions(this.bundle.entityStoreId);
      }
    });
  }

  loadPolicySetVersions(policySetId: string) {
    this.policySetService.getVersions(this.domainId, policySetId).subscribe((v) => {
      this.policySetVersions = v.sort((a: any, b: any) => b.version - a.version);
    });
  }

  loadSchemaVersions(schemaId: string) {
    this.authorizationSchemaService.getVersions(this.domainId, schemaId).subscribe((v) => {
      this.schemaVersions = v.sort((a: any, b: any) => b.version - a.version);
    });
  }

  loadEntityStoreVersions(entityStoreId: string) {
    this.entityStoreService.getVersions(this.domainId, entityStoreId).subscribe((v) => {
      this.entityStoreVersions = v.sort((a: any, b: any) => b.version - a.version);
    });
  }

  update() {
    const updatePayload = {
      name: this.bundle.name,
      description: this.bundle.description,
      policySetId: this.bundle.policySetId,
      policySetVersion: this.bundle.policySetVersion,
      policySetPinToLatest: this.bundle.policySetPinToLatest,
      schemaId: this.bundle.schemaId,
      schemaVersion: this.bundle.schemaVersion,
      schemaPinToLatest: this.bundle.schemaPinToLatest,
      entityStoreId: this.bundle.entityStoreId,
      entityStoreVersion: this.bundle.entityStoreVersion,
      entityStorePinToLatest: this.bundle.entityStorePinToLatest,
    };
    this.authorizationBundleService.update(this.domainId, this.bundle.id, updatePayload).subscribe((data) => {
      this.bundle = data;
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

  onPolicySetChange(policySetId: string) {
    this.bundle.policySetId = policySetId;
    if (policySetId) {
      const ps = this.policySets.find((p) => p.id === policySetId);
      if (ps) {
        this.bundle.policySetVersion = ps.latestVersion;
      }
      this.loadPolicySetVersions(policySetId);
    } else {
      this.policySetVersions = [];
      this.bundle.policySetVersion = 0;
    }
    this.formChanged = true;
  }

  onSchemaChange(schemaId: string) {
    this.bundle.schemaId = schemaId;
    if (schemaId) {
      const s = this.schemas.find((sc) => sc.id === schemaId);
      if (s) {
        this.bundle.schemaVersion = s.latestVersion;
      }
      this.loadSchemaVersions(schemaId);
    } else {
      this.schemaVersions = [];
      this.bundle.schemaVersion = 0;
    }
    this.formChanged = true;
  }

  onEntityStoreChange(entityStoreId: string) {
    this.bundle.entityStoreId = entityStoreId;
    if (entityStoreId) {
      const es = this.entityStores.find((e) => e.id === entityStoreId);
      if (es) {
        this.bundle.entityStoreVersion = es.latestVersion;
      }
      this.loadEntityStoreVersions(entityStoreId);
    } else {
      this.entityStoreVersions = [];
      this.bundle.entityStoreVersion = 0;
    }
    this.formChanged = true;
  }

  onPinToLatestChange() {
    this.formChanged = true;
  }
}
