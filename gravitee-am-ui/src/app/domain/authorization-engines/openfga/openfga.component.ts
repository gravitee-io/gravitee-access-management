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
import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormControl, Validators } from '@angular/forms';
import { transformer } from '@openfga/syntax-transformer';
import { graphBuilder } from '@openfga/frontend-utils';
import { Subscription } from 'rxjs';
import { filter, finalize, switchMap, tap } from 'rxjs/operators';
import { Network } from 'vis-network';
import { decodeTime } from 'ulid';
import './openfga-mode';
import { HttpErrorResponse } from '@angular/common/http';

import { OpenFGAService } from '../../../services/openfga.service';
import { AuthorizationEngineService } from '../../../services/authorization-engine.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { OrganizationService } from '../../../services/organization.service';
import { DialogService } from '../../../services/dialog.service';
import { PaginationService } from '../../../services/pagination.service';

interface AuthorizationEngine {
  id?: string;
  name: string;
  type: string;
  configuration: string;
}

interface Plugin {
  id: string;
  name: string;
  displayName?: string;
  icon?: string;
}

interface StoreInfo {
  name?: string;
  storeId?: string;
}

interface Tuple {
  user: string;
  relation: string;
  object: string;
}

interface PermissionResult {
  allowed: boolean;
}

interface AuthorizationModel {
  id: string;
  dsl: string;
  json: any;
}

@Component({
  selector: 'app-openfga',
  standalone: false,
  templateUrl: './openfga.component.html',
  styleUrls: ['./openfga.component.scss'],
})
export class OpenFGAComponent implements OnInit, OnDestroy {
  @ViewChild('graphContainer', { static: false }) graphContainer?: ElementRef;
  domainId: string;
  engineId: string;
  authorizationEngine: AuthorizationEngine = { name: '', type: '', configuration: '' };
  plugin: Plugin | null = null;
  storeId: string;
  authorizationModelId: string;
  storeInfo: StoreInfo | null = null;

  configuration: Record<string, any> = {};
  draftConfiguration: Record<string, any> = {};

  originalName: string = '';
  configurationSchema: Record<string, any> = {};
  configurationIsValid = false;

  authorizationModelDsl = '';
  authorizationModelJson: any | null = null;
  modelGraph: graphBuilder.GraphDefinition | null = null;
  selectedModelId: string = '';
  isEditing: boolean = false;
  originalModelId: string = '';
  editingModelDsl: string = '';
  private networkInstance: Network | null = null;
  hasLoadError = false;

  get selectedTabIndex(): number {
    return this.hasLoadError ? 3 : 0;
  }

  get codeMirrorOptions() {
    return {
      lineNumbers: true,
      theme: 'default',
      mode: 'text/x-openfga',
      readOnly: !this.isEditing,
      lineWrapping: true,
    };
  }

  get tuples(): Tuple[] {
    return this.tuplePagination.getState().items;
  }

  get isLoadingTuples(): boolean {
    return this.tuplePagination.getState().isLoading;
  }

  get hasNextTuplesPage(): boolean {
    return this.tuplePagination.hasNextPage();
  }

  get hasPreviousTuplesPage(): boolean {
    return this.tuplePagination.hasPreviousPage();
  }

  get authorizationModels(): AuthorizationModel[] {
    return this.modelPagination.getState().items;
  }

  readonly displayedColumns: string[] = ['user', 'relation', 'object', 'actions'];
  readonly tuplePageSize = 10;
  readonly modelPageSize = 50;

  userControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);
  relationControl = new FormControl('', [Validators.required]);
  objectControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);

  testUserControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);
  testRelationControl = new FormControl('', [Validators.required]);
  testObjectControl = new FormControl('', [Validators.required, Validators.pattern(/.*:.+/)]);

  permissionResult: PermissionResult | null = null;
  isCheckingPermission = false;

  private authorizationEnginePlugins: Record<string, Plugin> = {};
  private subscriptions = new Subscription();

  private tuplePagination: PaginationService<Tuple>;
  private modelPagination: PaginationService<AuthorizationModel>;

  constructor(
    private route: ActivatedRoute,
    private openFGAService: OpenFGAService,
    private authorizationEngineService: AuthorizationEngineService,
    private snackbarService: SnackbarService,
    private organizationService: OrganizationService,
    private dialogService: DialogService,
  ) {
    this.tuplePagination = new PaginationService<Tuple>();
    this.modelPagination = new PaginationService<AuthorizationModel>();
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.engineId = this.route.snapshot.params['engineId'];
    this.authorizationEnginePlugins = this.toPluginMap(this.route.snapshot.data['authorizationEnginePlugins']);

    this.load();
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
    if (this.networkInstance) {
      this.networkInstance.destroy();
    }
  }

  load() {
    this.subscriptions.add(
      this.authorizationEngineService.get(this.domainId, this.engineId).subscribe({
        next: (engine) => {
          this.authorizationEngine = engine;
          this.plugin = this.authorizationEnginePlugins?.[engine.type];
          const config = JSON.parse(engine.configuration || '{}');
          this.storeId = config.storeId;
          this.authorizationModelId = config.authorizationModelId;

          this.configuration = { ...config };
          this.draftConfiguration = { ...config };
          this.originalName = engine.name;

          this.loadAuthorizationEngineSchema(engine.type);

          const onLoadFailure = () => {
            this.hasLoadError = true;
          };

          this.loadAuthorizationModel(undefined, onLoadFailure);
          this.loadTuples(undefined, onLoadFailure);
        },
        error: () => {
          this.snackbarService.open('Failed to load authorization engine');
        },
      }),
    );
  }

  loadAuthorizationEngineSchema(engineType: string) {
    this.subscriptions.add(
      this.organizationService.authorizationEngineSchema(engineType).subscribe({
        next: (schema) => {
          this.configurationSchema = schema;
        },
        error: () => {
          this.snackbarService.open('Failed to load authorization engine schema');
        },
      }),
    );
  }

  loadAuthorizationModel(continuationToken?: string, onLoadFailure?: () => void) {
    this.modelPagination.setLoading(true);
    this.subscriptions.add(
      this.openFGAService
        .getStore(this.domainId, this.engineId)
        .pipe(
          tap((store) => {
            this.storeInfo = store;
          }),
          switchMap(() => this.openFGAService.listAuthorizationModels(this.domainId, this.engineId, this.modelPageSize, continuationToken)),
        )
        .subscribe({
          next: (response) => {
            this.modelPagination.setLoading(false);
            if (!response?.data || !Array.isArray(response.data)) {
              this.modelPagination.setItems([], null, this.modelPageSize);
              this.deselectModel();
              return;
            }
            const models = this.mapModels(response.data);
            const token = response.info?.continuationToken || null;
            this.modelPagination.setItems(models, token, this.modelPageSize);

            // Prefetch next page if current page is full
            if (models.length === this.modelPageSize && token) {
              this.prefetchNextModelPage(token);
            }

            if (models.length > 0) {
              if (!this.selectedModelId) {
                // Try to select the configured model, fallback to first (latest) model if not found
                const modelToSelect = models.find((m) => m.id === this.authorizationModelId) || models[0];
                this.selectedModelId = modelToSelect.id;
              }
              this.selectModel(this.selectedModelId);
            } else {
              this.deselectModel();
            }
          },
          error: () => {
            this.modelPagination.setLoading(false);
            this.modelPagination.setItems([], null, this.modelPageSize);
            this.authorizationModelDsl = '';
            this.authorizationModelJson = null;
            this.modelGraph = null;
            if (onLoadFailure) {
              onLoadFailure();
            } else {
              this.snackbarService.open('Failed to load store or authorization models');
            }
          },
        }),
    );
  }

  private mapModels(data: any[]): AuthorizationModel[] {
    return data.map((model: any) => {
      try {
        return {
          id: model.id,
          dsl: transformer.transformJSONStringToDSL(JSON.stringify(model)),
          json: model,
        };
      } catch (_error) {
        return {
          id: model.id,
          dsl: JSON.stringify(model, null, 2),
          json: model,
        };
      }
    });
  }

  selectModel(modelId: string) {
    const model = this.authorizationModels.find((m) => m.id === modelId);
    if (model) {
      this.selectedModelId = modelId;
      this.authorizationModelDsl = model.dsl;
      this.authorizationModelJson = model.json;

      if (this.authorizationModelJson) {
        const graphBuilderInstance = new graphBuilder.AuthorizationModelGraphBuilder(this.authorizationModelJson, {
          name: this.storeInfo?.name,
          id: this.storeInfo?.storeId,
        });
        this.modelGraph = graphBuilderInstance.graph;
        setTimeout(() => this.renderGraph(), 100);
      } else {
        this.modelGraph = null;
      }
    }
  }

  deselectModel() {
    this.selectedModelId = '';
    this.authorizationModelDsl = '';
    this.authorizationModelJson = null;
    this.modelGraph = null;
  }

  reviseModel() {
    if (this.isEditing) {
      return; // Already editing
    }

    const currentModel = this.authorizationModels.find((m) => m.id === this.selectedModelId);
    if (currentModel) {
      this.isEditing = true;
      this.originalModelId = this.selectedModelId;
      this.editingModelDsl = currentModel.dsl;
      this.authorizationModelDsl = this.editingModelDsl;
    }
  }

  saveModel() {
    if (!this.isEditing) {
      return;
    }

    try {
      // Transform DSL to JSON using the transformer
      const jsonModel = transformer.transformDSLToJSONObject(this.authorizationModelDsl);

      this.openFGAService.addAuthorizationModel(this.domainId, this.engineId, jsonModel).subscribe({
        next: ({ authorizationModelId }) => {
          console.log('Model saved successfully:', authorizationModelId);
          this.snackbarService.open('Authorization model saved successfully');
          this.stopEdit();
          this.selectedModelId = authorizationModelId;
          // Reset pagination state and reload the models to get the updated list
          this.modelPagination.reset();
          this.loadAuthorizationModel();
        },
        error: (error: unknown) => {
          console.error('Error saving model:', error);
          this.snackbarService.open('Failed to save authorization model');
        },
      });
    } catch (error) {
      this.snackbarService.open('Invalid DSL syntax. Please check your model definition.');
      console.error('DSL transformation error:', error);
    }
  }

  stopEdit() {
    this.isEditing = false;
    this.originalModelId = '';
    this.editingModelDsl = '';
  }

  cancelEdit() {
    if (!this.isEditing) {
      return;
    }

    this.stopEdit();

    // Restore the original model
    this.selectModel(this.selectedModelId);
  }

  isCurrentModelActive(): boolean {
    // If no model is explicitly configured as active, the first model of the first page is considered active
    if (!this.authorizationModelId) {
      return this.authorizationModels.length > 0 && this.selectedModelId === this.authorizationModels[0].id;
    }
    // Otherwise, check if the selected model matches the configured active model
    return this.isCurrentModelExplicitlyActive();
  }

  isCurrentModelExplicitlyActive(): boolean {
    return this.selectedModelId === this.authorizationModelId;
  }

  getSelectedModelTimestamp(): Date | null {
    if (!this.selectedModelId) {
      return null;
    }
    const timestamp = decodeTime(this.selectedModelId);
    return new Date(timestamp);
  }

  applyModel() {
    if (!this.selectedModelId || this.isCurrentModelExplicitlyActive()) {
      return;
    }

    try {
      const currentConfig = JSON.parse(this.authorizationEngine.configuration || '{}');
      const updatedConfig = {
        ...currentConfig,
        authorizationModelId: this.selectedModelId,
      };

      const updatedEngine: AuthorizationEngine = {
        ...this.authorizationEngine,
        name: this.authorizationEngine.name,
        configuration: JSON.stringify(updatedConfig),
      };

      this.subscriptions.add(
        this.authorizationEngineService.update(this.domainId, this.engineId, updatedEngine).subscribe({
          next: (engine) => {
            this.authorizationEngine = engine;
            const config = JSON.parse(engine.configuration || '{}');
            this.configuration = { ...config };
            this.draftConfiguration = { ...config };
            this.storeId = config.storeId;
            this.authorizationModelId = config.authorizationModelId;
            this.snackbarService.open('Authorization model applied successfully');
          },
          error: (error: unknown) => {
            this.snackbarService.open('Failed to apply authorization model');
            console.error('Error applying model:', error);
          },
        }),
      );
    } catch (error) {
      this.snackbarService.open('Invalid configuration format');
      console.error('Configuration parsing error:', error);
    }
  }

  loadTuples(continuationToken?: string, onLoadFailure?: () => void) {
    this.tuplePagination.setLoading(true);
    this.subscriptions.add(
      this.openFGAService.listTuples(this.domainId, this.engineId, this.tuplePageSize, continuationToken).subscribe({
        next: (response) => {
          const tuples = response.data || [];
          const token = response.info?.continuationToken || null;
          this.tuplePagination.setItems(tuples, token, this.tuplePageSize);
          this.tuplePagination.setLoading(false);

          // Prefetch next page if current page is full
          if (tuples.length === this.tuplePageSize && token) {
            this.prefetchNextPage(token);
          }
        },
        error: () => {
          this.tuplePagination.setLoading(false);
          if (onLoadFailure) {
            onLoadFailure();
          } else {
            this.snackbarService.open('Failed to load tuples');
          }
        },
      }),
    );
  }

  private prefetchNextPage(token: string) {
    this.subscriptions.add(
      this.openFGAService.listTuples(this.domainId, this.engineId, this.tuplePageSize, token).subscribe({
        next: (response) => {
          const prefetchedData = response.data || [];
          const prefetchedToken = response.info?.continuationToken || null;
          this.tuplePagination.setPrefetchedData(prefetchedData, prefetchedToken);
        },
        error: () => {
          this.tuplePagination.setPrefetchedData([], null);
        },
      }),
    );
  }

  private prefetchNextModelPage(token: string) {
    this.subscriptions.add(
      this.openFGAService.listAuthorizationModels(this.domainId, this.engineId, this.modelPageSize, token).subscribe({
        next: (response) => {
          const prefetchedData = this.mapModels(response);
          const prefetchedToken = response.info?.continuationToken || null;
          this.modelPagination.setPrefetchedData(prefetchedData, prefetchedToken);
        },
        error: () => {
          this.modelPagination.setPrefetchedData([], null);
        },
      }),
    );
  }

  nextPage() {
    if (!this.hasNextTuplesPage) {
      return;
    }

    try {
      this.tuplePagination
        .nextPage(this.tuplePageSize, (token) => this.openFGAService.listTuples(this.domainId, this.engineId, this.tuplePageSize, token))
        .subscribe({
          next: (response) => {
            const tuples = response.data || [];
            const token = response.info?.continuationToken || null;
            this.tuplePagination.setItems(tuples, token, this.tuplePageSize);

            // Prefetch next page if current page is full
            if (tuples.length === this.tuplePageSize && token) {
              this.prefetchNextPage(token);
            }
          },
          error: () => {
            this.snackbarService.open('Failed to load next page');
          },
        });
    } catch {
      this.snackbarService.open('No more pages available');
    }
  }

  previousPage() {
    this.tuplePagination
      .previousPage((token) => this.openFGAService.listTuples(this.domainId, this.engineId, this.tuplePageSize, token))
      .subscribe({
        next: (response) => {
          const tuples = response.data || [];
          const token = response.info?.continuationToken || null;
          this.tuplePagination.setItems(tuples, token, this.tuplePageSize);
        },
        error: () => {
          this.snackbarService.open('Failed to load previous page');
        },
      });
  }

  nextModelPage() {
    if (!this.hasNextTuplesPage) {
      return;
    }

    try {
      this.modelPagination
        .nextPage(this.modelPageSize, (token) =>
          this.openFGAService.listAuthorizationModels(this.domainId, this.engineId, this.modelPageSize, token),
        )
        .subscribe({
          next: (response) => {
            const models = this.mapModels(response.data || []);
            const token = response.info?.continuationToken || null;
            this.modelPagination.setItems(models, token, this.modelPageSize);

            // Prefetch next page if current page is full
            if (models.length === this.modelPageSize && token) {
              this.prefetchNextModelPage(token);
            }
          },
          error: () => {
            this.snackbarService.open('Failed to load next page');
          },
        });
    } catch {
      this.snackbarService.open('No more pages available');
    }
  }

  previousModelPage() {
    this.modelPagination
      .previousPage((token) => this.openFGAService.listAuthorizationModels(this.domainId, this.engineId, this.modelPageSize, token))
      .subscribe({
        next: (response) => {
          const models = this.mapModels(response.data || []);
          const token = response.info?.continuationToken || null;
          this.modelPagination.setItems(models, token, this.modelPageSize);
        },
        error: () => {
          this.snackbarService.open('Failed to load previous page');
        },
      });
  }

  isAddTupleValid(): boolean {
    return this.userControl.valid && this.relationControl.valid && this.objectControl.valid;
  }

  addTuple() {
    if (!this.isAddTupleValid()) {
      return;
    }

    const tuple = {
      user: this.userControl.value || '',
      relation: this.relationControl.value || '',
      object: this.objectControl.value || '',
    };

    const exists = this.tuples.some((t) => t.user === tuple.user && t.relation === tuple.relation && t.object === tuple.object);

    if (exists) {
      this.snackbarService.open('This tuple already exists');
      return;
    }

    this.openFGAService.addTuple(this.domainId, this.engineId, tuple).subscribe({
      next: () => {
        this.tuplePagination.reset();
        this.loadTuples();
        this.userControl.reset();
        this.relationControl.reset();
        this.objectControl.reset();
        this.snackbarService.open('Tuple added successfully');
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse) {
          if (error.status === 400) {
            this.snackbarService.open('Invalid tuple: please check the values');
          } else {
            this.snackbarService.open('Failed to add tuple');
          }
        } else {
          this.snackbarService.open('Unexpected error occurred');
        }
      },
    });
  }

  deleteTuple(tuple: Tuple) {
    this.dialogService
      .confirm('Delete Tuple', 'Are you sure you want to delete this tuple?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.openFGAService.deleteTuple(this.domainId, this.engineId, tuple)),
      )
      .subscribe({
        next: () => {
          this.tuplePagination.reset();
          this.loadTuples();
          this.snackbarService.open('Tuple deleted successfully');
        },
        error: () => {
          this.snackbarService.open('Failed to delete tuple');
        },
      });
  }

  isCheckPermissionValid(): boolean {
    return this.testUserControl.valid && this.testRelationControl.valid && this.testObjectControl.valid;
  }

  checkPermission() {
    if (!this.isCheckPermissionValid()) {
      return;
    }

    this.isCheckingPermission = true;
    this.permissionResult = null;

    const permissionRequest = {
      user: this.testUserControl.value || '',
      relation: this.testRelationControl.value || '',
      object: this.testObjectControl.value || '',
    };

    this.openFGAService.checkPermission(this.domainId, this.engineId, permissionRequest).subscribe({
      next: (response) => {
        this.isCheckingPermission = false;
        this.permissionResult = response;
        const message = response.allowed ? 'Permission allowed' : 'Permission denied';
        this.snackbarService.open(message);
      },
      error: () => {
        this.isCheckingPermission = false;
        this.snackbarService.open('Failed to check permission');
      },
    });
  }

  isConfigurationDirty(): boolean {
    const configChanged = JSON.stringify(this.draftConfiguration) !== JSON.stringify(this.configuration);
    const nameChanged = this.authorizationEngine?.name !== this.originalName;
    return configChanged || nameChanged;
  }

  onConfigurationChanged(configurationWrapper: { isValid: boolean; configuration: Record<string, any> }) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.draftConfiguration = configurationWrapper.configuration || {};
  }

  isSaving = false;

  saveConfiguration() {
    if (!this.isConfigurationDirty() || this.isSaving) {
      return;
    }

    this.isSaving = true;

    const updatedEngine: AuthorizationEngine = {
      ...this.authorizationEngine,
      name: this.authorizationEngine.name,
      configuration: JSON.stringify(this.draftConfiguration),
    };

    this.subscriptions.add(
      this.authorizationEngineService
        .update(this.domainId, this.engineId, updatedEngine)
        .pipe(
          finalize(() => {
            this.isSaving = false;
          }),
        )
        .subscribe({
          next: (engine) => {
            this.authorizationEngine = engine;
            const config = JSON.parse(engine.configuration || '{}');
            this.configuration = { ...config };
            this.draftConfiguration = { ...config };
            this.originalName = engine.name;
            this.storeId = config.storeId;
            this.authorizationModelId = config.authorizationModelId;
            this.selectedModelId = null;
            this.snackbarService.open('Configuration saved successfully');

            this.tuplePagination.reset();
            this.modelPagination.reset();
            this.loadAuthorizationModel();
            this.loadTuples();
            this.hasLoadError = false;
          },
        }),
    );
  }

  private renderGraph() {
    if (!this.modelGraph || !this.graphContainer) {
      return;
    }

    // Destroy previous instance
    if (this.networkInstance) {
      this.networkInstance.destroy();
    }

    // Prepare nodes and edges for vis-network
    const nodes = this.modelGraph.nodes.map((node) => ({
      id: node.id,
      label: node.label,
      group: node.group || 'default',
    }));

    const edges = this.modelGraph.edges.map((edge) => ({
      from: edge.from,
      to: edge.to,
      label: edge.label,
      dashes: edge.dashes || false,
      arrows: 'to',
    }));

    // vis-network options
    const options: any = {
      nodes: {
        shape: 'box',
        margin: 10,
        widthConstraint: {
          maximum: 200,
        },
        font: {
          size: 14,
        },
      },
      edges: {
        arrows: {
          to: { enabled: true, scaleFactor: 0.5 },
        },
        smooth: {
          enabled: true,
          type: 'cubicBezier',
          roundness: 0.5,
        },
      },
      physics: {
        enabled: true,
        barnesHut: {
          gravitationalConstant: -2000,
          springConstant: 0.001,
          springLength: 200,
        },
        stabilization: {
          iterations: 150,
        },
      },
      layout: {
        hierarchical: {
          enabled: true,
          direction: 'LR',
          sortMethod: 'directed',
        },
      },
    };

    // Create network
    this.networkInstance = new Network(this.graphContainer.nativeElement, { nodes, edges }, options);
  }

  private toPluginMap(plugins: Plugin[] | Record<string, Plugin>): Record<string, Plugin> {
    if (!Array.isArray(plugins)) {
      return plugins || {};
    }
    return plugins.reduce(
      (acc, plugin) => {
        if (plugin?.id) {
          acc[plugin.id] = plugin;
        }
        return acc;
      },
      {} as Record<string, Plugin>,
    );
  }
}
