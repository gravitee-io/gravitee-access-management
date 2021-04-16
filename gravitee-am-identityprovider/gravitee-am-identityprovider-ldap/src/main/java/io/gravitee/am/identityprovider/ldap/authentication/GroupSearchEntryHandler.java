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
package io.gravitee.am.identityprovider.ldap.authentication;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.ldaptive.*;
import org.ldaptive.handler.HandlerResult;
import org.ldaptive.handler.SearchEntryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides post groups search handling of a search entry (i.e the authenticated user)
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupSearchEntryHandler implements SearchEntryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupSearchEntryHandler.class);
    private static final String MEMBEROF_ATTRIBUTE = "memberOf";
    private String baseDn;
    private String searchFilter;
    private SearchScope searchScope;
    private String[] returnAttributes;

    @Override
    public HandlerResult<SearchEntry> handle(Connection conn, SearchRequest request, SearchEntry entry) throws LdapException {
        try {
            final SearchFilter filter = new SearchFilter();
            filter.setFilter(searchFilter);
            filter.setParameter(0, entry.getDn());

            final SearchRequest searchRequest = new SearchRequest();
            searchRequest.setBaseDn(baseDn);
            searchRequest.setReturnAttributes(returnAttributes);
            searchRequest.setSearchScope(searchScope);
            searchRequest.setSearchFilter(filter);

            final SearchOperation searchOperation = new SearchOperation(conn);
            SearchResult searchResult = searchOperation.execute(searchRequest).getResult();

            // update search entry
            Collection<LdapEntry> groupEntries = searchResult.getEntries();
            String[] groups = groupEntries
                .stream()
                .map(
                    groupEntry ->
                        groupEntry
                            .getAttributes()
                            .stream()
                            .map(ldapAttribute -> ldapAttribute.getStringValue())
                            .collect(Collectors.toList())
                )
                .flatMap(List::stream)
                .toArray(size -> new String[size]);
            entry.addAttribute(new LdapAttribute(MEMBEROF_ATTRIBUTE, groups));
        } catch (Exception ex) {
            LOGGER.warn("No group found for user {}", entry.getDn(), ex);
        } finally {
            return new HandlerResult<>(entry);
        }
    }

    @Override
    public void initializeRequest(SearchRequest request) {
        // nothing to do
    }

    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter;
    }

    public void setSearchScope(SearchScope searchScope) {
        this.searchScope = searchScope;
    }

    public void setReturnAttributes(String[] returnAttributes) {
        this.returnAttributes = returnAttributes;
    }
}
