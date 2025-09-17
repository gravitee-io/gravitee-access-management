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
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';

import { OpenFGAService } from '../../../services/openfga.service';
import { SnackbarService } from '../../../services/snackbar.service';

interface OpenFGAStore {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

@Component({
  selector: 'app-openfga',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatStepperModule,
    MatTabsModule,
    MatSelectModule,
  ],
  templateUrl: './openfga.component.html',
  styleUrls: ['./openfga.component.scss']
})
export class OpenFGAComponent implements OnInit {
  domainId: string;

  // Connection state
  serverUrl = 'http://localhost:8080';
  isConnecting = false;
  isConnected = false;

  // Store management
  stores: OpenFGAStore[] = [];
  selectedStoreId = '';
  isLoadingStores = false;
  showCreateStore = false;
  newStoreName = '';
  isCreatingStore = false;

  // Authorization model
  authorizationModel = ``;

  // Tuples
  tuples = [
    { user: 'alice', relation: 'owner', object: 'document:1' },
    { user: 'bob', relation: 'reader', object: 'document:1' }
  ];

  newTuple = {
    user: '',
    relation: '',
    object: ''
  };

  constructor(
    private route: ActivatedRoute,
    private openFGAService: OpenFGAService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
  }

  connect() {
    if (!this.serverUrl) {
      this.snackbarService.open('Please enter a server URL');
      return;
    }

    this.isConnecting = true;

    this.openFGAService.connect(this.domainId, this.serverUrl).subscribe({
      next: (response) => {
        this.isConnected = response.status;
        this.isConnecting = false;
        this.snackbarService.open('Successfully connected to OpenFGA server');
        this.stores = response.stores;
      },
      error: (error) => {
        this.isConnecting = false;
        this.snackbarService.open('Failed to connect to OpenFGA server');
        console.error('Connection error:', error);
      }
    });
  }

  disconnect() {
    this.isConnected = false;
    this.stores = [];
    this.selectedStoreId = '';
    this.snackbarService.open('Disconnected from OpenFGA server');
  }

  loadStores() {
    this.isLoadingStores = true;

    this.openFGAService.listStores(this.domainId).subscribe({
      next: (stores) => {
        this.stores = stores;
        this.isLoadingStores = false;
      },
      error: (error) => {
        this.isLoadingStores = false;
        this.snackbarService.open('Failed to load stores');
        console.error('Load stores error:', error);
      }
    });
  }

  createStore() {
    if (!this.newStoreName.trim()) {
      this.snackbarService.open('Please enter a store name');
      return;
    }

    this.isCreatingStore = true;

    this.openFGAService.createStore(this.domainId, this.newStoreName.trim()).subscribe({
      next: (newStore) => {
        this.stores.push(newStore);
        this.selectedStoreId = newStore.id;
        this.newStoreName = '';
        this.showCreateStore = false;
        this.isCreatingStore = false;
        this.snackbarService.open('Store created successfully');
      },
      error: (error) => {
        this.isCreatingStore = false;
        this.snackbarService.open('Failed to create store');
        console.error('Create store error:', error);
      }
    });
  }

  cancelCreateStore() {
    this.showCreateStore = false;
    this.newStoreName = '';
  }

  onStoreSelected() {
    const selectedStore = this.stores.find(store => store.id === this.selectedStoreId);
    if (selectedStore) {
      this.snackbarService.open(`Selected store: ${selectedStore.name}`);
    }
  }

  // Check if we can proceed to authorization management
  get canManageAuthorization(): boolean {
    return this.isConnected && !!this.selectedStoreId;
  }

  // Helper method to get selected store name
  get selectedStoreName(): string {
    return this.stores.find(s => s.id === this.selectedStoreId)?.name || '';
  }

  updateAuthorizationModel() {
    if (!this.authorizationModel.trim()) {
      this.snackbarService.open('Please enter an authorization model');
      return;
    }

    this.openFGAService.updateAuthorizationModel(this.domainId, this.authorizationModel, this.selectedStoreId).subscribe({
      next: (response) => {
        this.snackbarService.open('Authorization model updated successfully');
      },
      error: (error) => {
        this.snackbarService.open('Failed to update authorization model');
        console.error('Update authorization model error:', error);
      }
    });
  }

  addTuple() {
    if (!this.newTuple.user || !this.newTuple.relation || !this.newTuple.object) {
      this.snackbarService.open('Please fill in all tuple fields');
      return;
    }

    this.openFGAService.addTuple(this.domainId, this.newTuple).subscribe({
      next: (response) => {
        this.tuples.push({ ...this.newTuple });
        // Reset form
        this.newTuple = {
          user: '',
          relation: '',
          object: ''
        };
        this.snackbarService.open('Tuple added successfully');
      },
      error: (error) => {
        this.snackbarService.open('Failed to add tuple');
        console.error('Add tuple error:', error);
      }
    });
  }

  removeTuple(index: number) {
    this.tuples.splice(index, 1);
    this.snackbarService.open('Tuple removed successfully');
  }

  uploadTuples() {
    if (this.tuples.length === 0) {
      this.snackbarService.open('No tuples to upload');
      return;
    }

    this.openFGAService.uploadTuples(this.domainId, this.tuples).subscribe({
      next: (response) => {
        this.snackbarService.open(`${this.tuples.length} tuples uploaded successfully`);
      },
      error: (error) => {
        this.snackbarService.open('Failed to upload tuples');
        console.error('Upload tuples error:', error);
      }
    });
  }

  checkPermission() {
    const permissionRequest = {
      user: 'alice',
      relation: 'reader',
      object: 'document:1'
    };

    this.openFGAService.checkPermission(this.domainId, permissionRequest).subscribe({
      next: (response) => {
        const message = response.allowed ? 'Permission allowed' : 'Permission denied';
        this.snackbarService.open(message);
      },
      error: (error) => {
        this.snackbarService.open('Failed to check permission');
        console.error('Check permission error:', error);
      }
    });
  }

  formatTuple(tuple: any): string {
    return `${tuple.user} ${tuple.relation} ${tuple.object}`;
  }
}
