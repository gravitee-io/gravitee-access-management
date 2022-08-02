/**
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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import io.gravitee.am.repository.management.api.ThemeRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ThemeMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoThemeRepository extends AbstractManagementMongoRepository implements ThemeRepository {

    private MongoCollection<ThemeMongo> themesCollection;

    @PostConstruct
    public void init() {
        themesCollection = mongoOperations.getCollection("themes", ThemeMongo.class);
        super.init(themesCollection);
        super.createIndex(themesCollection, new Document(FIELD_ID, 1));
        super.createIndex(themesCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1));
    }

    @Override
    public Maybe<Theme> findById(String id) {
        return Observable.fromPublisher(themesCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Theme> create(Theme item) {
        ThemeMongo theme = convert(item);
        theme.setId(theme.getId() == null ? RandomString.generate() : theme.getId());
        return Single.fromPublisher(themesCollection.insertOne(theme)).flatMap(success -> { item.setId(theme.getId()); return Single.just(item); });
    }

    @Override
    public Single<Theme> update(Theme item) {
        ThemeMongo theme = convert(item);
        return Single.fromPublisher(themesCollection.replaceOne(eq(FIELD_ID, theme.getId()), theme)).flatMap(updateResult -> Single.just(item));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(themesCollection.deleteOne(eq(FIELD_ID, id)));

    }

    @Override
    public Maybe<Theme> findByReference(ReferenceType referenceType, String referenceId) {
        return Observable.fromPublisher(themesCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))).first())
                .firstElement()
                .map(this::convert);
    }


    private Theme convert(ThemeMongo themeMongo) {
        if (themeMongo == null) {
            return null;
        }

        Theme theme = new Theme();
        theme.setId(themeMongo.getId());
        theme.setCss(themeMongo.getCss());
        theme.setCreatedAt(themeMongo.getCreatedAt());
        theme.setUpdatedAt(themeMongo.getUpdatedAt());
        theme.setLogoUrl(themeMongo.getLogoUrl());
        theme.setLogoWidth(themeMongo.getLogoWidth());
        theme.setFaviconUrl(themeMongo.getFaviconUrl());
        theme.setReferenceId(themeMongo.getReferenceId());
        theme.setReferenceType(ReferenceType.valueOf(themeMongo.getReferenceType()));
        theme.setPrimaryTextColorHex(themeMongo.getPrimaryTextColorHex());
        theme.setPrimaryButtonColorHex(themeMongo.getPrimaryButtonColorHex());
        theme.setSecondaryButtonColorHex(themeMongo.getSecondaryButtonColorHex());

        return theme;
    }

    private ThemeMongo convert(Theme theme) {
        if (theme == null) {
            return null;
        }

        ThemeMongo themeMongo = new ThemeMongo();
        themeMongo.setId(theme.getId());
        themeMongo.setCss(theme.getCss());
        themeMongo.setCreatedAt(theme.getCreatedAt());
        themeMongo.setUpdatedAt(theme.getUpdatedAt());
        themeMongo.setLogoUrl(theme.getLogoUrl());
        themeMongo.setLogoWidth(theme.getLogoWidth());
        themeMongo.setFaviconUrl(theme.getFaviconUrl());
        themeMongo.setReferenceId(theme.getReferenceId());
        themeMongo.setReferenceType(theme.getReferenceType().name());
        themeMongo.setPrimaryTextColorHex(theme.getPrimaryTextColorHex());
        themeMongo.setPrimaryButtonColorHex(theme.getPrimaryButtonColorHex());
        themeMongo.setSecondaryButtonColorHex(theme.getSecondaryButtonColorHex());

        return themeMongo;
    }
}
