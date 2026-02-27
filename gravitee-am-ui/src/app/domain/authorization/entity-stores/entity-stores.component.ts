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

import { EntityStoreService } from '../../../services/entity-store.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-entity-stores',
  templateUrl: './entity-stores.component.html',
  styleUrls: ['./entity-stores.component.scss'],
  standalone: false,
})
export class EntityStoresComponent implements OnInit {
  entityStores: any[];
  domainId: string;

  constructor(
    private entityStoreService: EntityStoreService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.entityStores = this.route.snapshot.data['entityStores'] || [];
  }

  loadEntityStores() {
    this.entityStoreService.findByDomain(this.domainId).subscribe((entityStores) => (this.entityStores = entityStores));
  }

  get isEmpty() {
    return !this.entityStores || this.entityStores.length === 0;
  }

  delete(id: string, event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Entity Store', 'Are you sure you want to delete this entity store?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.entityStoreService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Entity store deleted');
          this.loadEntityStores();
        }),
      )
      .subscribe();
  }
}
