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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {DomainService} from '../../../services/domain.service';
import {DialogService} from '../../../services/dialog.service';
import {SnackbarService} from '../../../services/snackbar.service';
import {AuthService} from '../../../services/auth.service';
import {NavbarService} from '../../../components/navbar/navbar.service';
import {DictionaryDialog} from '../../../components/dialog/dictionary/dictionary-dialog.component';
import {MatDialog} from '@angular/material/dialog';
import {I18nDictionaryService} from '../../../services/dictionary.service';
import {i18nLanguages} from './i18nLanguages';

interface Dictionary {
  id?: string;
  locale: string;
  name: string;
  entries?: object;
}

@Component({
  selector: 'app-dictionaries',
  templateUrl: './dictionaries.component.html',
  styleUrls: ['./dictionaries.component.scss']
})
export class DomainSettingsDictionariesComponent implements OnInit {
  domain: any = {};
  readonly = false;
  dictionaries: Dictionary[] = [];
  translations: any[] = [];
  selectedDictionary: Dictionary = {locale: '', name: ''};
  languageCodes: any[];
  dictsToSave: Dictionary[] = [];
  dictsToDelete: any[] = [];
  codeMirrorConfig: any = {lineNumbers: true, readOnly: false};
  formContent = '';
  displayCodeMirror: boolean;
  formChangedTranslations: boolean;
  private selectedTab = 0;
  private originalFormContent: string;

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private dictionaryService: I18nDictionaryService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService,
              private navbarService: NavbarService,
              private dialog: MatDialog) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.readonly = !this.authService.hasPermissions(['domain_i18n_dictionary_update']);
    this.dictionaries = this.route.snapshot.data['dictionaries'] || [];
    const dictionaryCodes = this.dictionaries.map(dict => dict.locale);
    this.languageCodes = Object.entries(i18nLanguages).filter(language => !dictionaryCodes.includes(language[0]));
    this.selectedDictionary = this.dictionaries[0];
    if (this.selectedDictionary) {
      this.changeTranslation();
    }
    this.formContent = JSON.stringify(this.entriesToObject(), null, '\t');
    this.originalFormContent = (' ' + this.formContent).slice(1);
  }

  addLanguage() {
    const dialogRef = this.dialog.open(DictionaryDialog,
      {
        data: {
          title: 'Add a new language',
          prop1Label: 'Language code',
          prop2Label: 'Display name',
          languageCodes: this.languageCodes
        }
      });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        const locale = result.prop1;
        const name = result.prop2;
        const tempDicts = [...this.dictionaries];
        const newDict = {locale: locale, name: name, entries: {}};
        tempDicts.push(newDict);
        this.dictionaries = tempDicts;
        this.dictsToSave.push(newDict);
        this.languageCodes = this.languageCodes.filter(code => code[0] !== locale);
        this.saveLanguages();
      }
    });
  }

  deleteLanguage(row, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Language', 'Deleting the language will delete all the saved translations. Are you sure you want to delete this language?')
      .subscribe(res => {
        if (res) {
          if (row.id) {
            this.dictsToDelete.push(row.id);
          } else {
            this.dictsToSave = this.dictsToSave.filter(dict => dict.locale !== row.locale)
          }
          this.dictionaries = this.dictionaries.filter(dict => dict.locale !== row.locale);
          if (this.selectedDictionary.locale === row.locale) {
            this.translations = [];
          }
          this.languageCodes.push([row.locale, row.name]);
          this.languageCodes.sort((a, b) => a[0].localeCompare(b[0]));
          this.saveLanguages();
        }
      });
  }

  deleteTranslation(rowIndex, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete translation', 'Are you sure you want to delete this translation?')
      .subscribe(res => {
        const deleteCount = 1;
        if (res) {
          this.translations.splice(rowIndex, deleteCount);
          this.translations = [...this.translations];
          this.saveTranslations();
        }
      });
  }

  changeTranslation() {
    this.translations = [];

    Object.entries(this.selectedDictionary.entries).forEach(([k, v]) => {
      this.translations.push({key: k, message: v});
    });

    if (this.selectedTab === 1) {
      this.onTabSelectedChanged({index: 1})
    }
  }

  addTranslation() {
    const tempEntries = [...this.translations];
    if (this.selectedDictionary) {
      const dialogRef = this.dialog.open(DictionaryDialog,
        {
          data: {
            title: 'Add a new translation',
            prop1Label: 'Key',
            prop2Label: 'Value'
          }
        });

      dialogRef.afterClosed().subscribe(result => {
        const entryKey = result.prop1;
        const value = result.prop2;
        tempEntries.push({key: entryKey, message: value});
        this.translations = tempEntries;
        this.saveTranslations();
      });
    } else {
      this.snackbarService.open('Please select a language');
    }
  }

  saveTranslations() {
    if (!this.selectedDictionary.id) {
      this.dictionaryService.create(this.domain.id, {
        locale: this.selectedDictionary.locale,
        name: this.selectedDictionary.name
      }).subscribe(created => {
        this.updateId(created);
        this.update();
      });
    } else if (this.selectedDictionary) {
      this.update();
    } else {
      this.snackbarService.open('Please select a language');
    }
  }

  private updateId(created) {
    this.dictionaries.forEach(dict => {
      if (dict.locale === created.locale) {
        dict.id = created.id;
      }
    });
  }

  private update() {
    const updateEntries = this.selectedTab === 0 ? this.entriesToObject() : JSON.parse(this.formContent);
    this.dictionaryService.update(this.domain.id, this.selectedDictionary.id,
      {entries: updateEntries}).subscribe(result => {
      if (result) {
        this.snackbarService.open('Translations saved');
        this.formChangedTranslations = false;
        for (const dictionary of this.dictionaries) {
          if (dictionary.id === this.selectedDictionary.id) {
            dictionary.entries = updateEntries;
            this.originalFormContent = JSON.stringify(updateEntries, null, '\t');
            break;
          }
        }
      }
    });
  }

  private entriesToObject() {
    // @ts-ignore
    return Object.fromEntries(this.translations.flatMap(row => [Object.values(row)]));
  }

  saveLanguages() {
    const dictsToSave = [...this.dictsToSave];
    const dictsToDelete = [...this.dictsToDelete];
    dictsToSave.forEach(language => {
      this.dictionaryService.create(this.domain.id, {
        locale: language.locale,
        name: language.name
      }).subscribe(created => {
        this.updateId(created);
        this.dictsToSave = [];
        this.snackbarService.open('Languages saved');
      });
    });
    dictsToDelete.forEach(id => {
      this.dictionaryService.delete(this.domain.id, id).subscribe(() => {
        this.dictionaries = this.dictionaries.filter(dict => dict.id !== id);
        this.dictsToDelete = [];
        if (this.selectedDictionary.id === id) {
          this.translations = [];
        }
        this.snackbarService.open('Languages saved');
      });
    })
  }

  onFileSelected(event) {
    const file: File = event.target.files[0];

    if (file) {
      file.text().then(content => {
        this.formContent = content;
        try {
          const parse = JSON.parse(content);
          const entries = [];
          Object.entries(parse).forEach(mapEntry => {
            entries.push(mapEntry);
          });
        } catch (e) {
          this.snackbarService.open('JSON file was invalid');
        }
      });
    }
  }

  onTabSelectedChanged(e) {
    this.selectedTab = e.index;
    if (e.index === 1) {
      this.formContent = JSON.stringify(this.entriesToObject(), null, '\t');
      this.displayCodeMirror = true;
    } else {
      this.displayCodeMirror = false;
      const parse = JSON.parse(this.formContent);
      this.translations = [];
      Object.entries(parse).forEach(([k, v]) => {
        this.translations.push({key: k, message: v});
      });
    }
  }

  onContentChanges(e) {
    if (e !== this.originalFormContent && !this.formChangedTranslations) {
      this.formChangedTranslations = true;
    }
  }

  enableSaveChangeButton() {
    if (!this.formChangedTranslations) {
      this.formChangedTranslations = true;
    }
  }
}
