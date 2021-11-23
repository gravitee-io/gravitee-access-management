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
package io.gravitee.am.identityprovider.ldap.common.authentication;

import org.ldaptive.*;
import org.ldaptive.handler.HandlerResult;
import org.ldaptive.handler.RecursiveEntryHandler;
import org.ldaptive.handler.SearchEntryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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

    private final boolean recursiveSearch;

    public GroupSearchEntryHandler() {
        this.recursiveSearch = false;
    }

    public GroupSearchEntryHandler(boolean recursiveSearch) {
        this.recursiveSearch = recursiveSearch;
    }

    @Override
    public HandlerResult<SearchEntry> handle(Connection conn, SearchRequest request, SearchEntry entry) throws LdapException {
        try {
            Set<LdapEntry> allGroups = new HashSet<>();
            // search groups where the user is directly referenced as a member
            final Collection<LdapEntry> directGroups = groupSearch(conn, entry.getDn());
            if (recursiveSearch) {
                // for each group look for ancestor in order to retrieve all group hierarchy
                recursiveGroupsSearch(conn, directGroups, allGroups);
            } else {
                allGroups.addAll(directGroups);
            }

            final String[] groupName = allGroups.stream().map(groupEntry -> groupEntry.getAttributes()
                            .stream()
                            .map(ldapAttribute -> ldapAttribute.getStringValue())
                            .collect(Collectors.toList()))
                    .flatMap(List::stream)
                    .toArray(size -> new String[size]);

            entry.addAttribute(new LdapAttribute(MEMBEROF_ATTRIBUTE, groupName));
        } catch (Exception ex) {
            LOGGER.warn("No group found for user {}", entry.getDn(), ex);
        } finally {
            return new HandlerResult<>(entry);
        }
    }

    private void recursiveGroupsSearch(Connection conn, Collection<LdapEntry> groupsToSearch, Set<LdapEntry> accGroups) throws LdapException {
        List<LdapEntry> ancestors = new ArrayList<>();
        Set<String> processedGroupDNs = accGroups.stream().map(LdapEntry::getDn).collect(Collectors.toSet());
        for (LdapEntry grp: groupsToSearch) {
            // check if the group is already pushed into the accumulator list to avoid infinite loops
            if (!processedGroupDNs.contains(grp.getDn())) {
                accGroups.add(grp);
                processedGroupDNs.add(grp.getDn());
                // look for group ancestors
                final Collection<LdapEntry> foundGroups = groupSearch(conn, grp.getDn());
                if (!foundGroups.isEmpty()) {
                    ancestors.addAll(foundGroups);
                }
            }
        }
        if (!ancestors.isEmpty()) {
            // for the current groups we found ancestors, search deep into the group tree
            recursiveGroupsSearch(conn, ancestors, accGroups);
        }
    }

    private Collection<LdapEntry> groupSearch(Connection conn, String dn) throws LdapException {
        final SearchFilter filter = new SearchFilter();
        filter.setFilter(searchFilter);
        filter.setParameter(0, dn);

        final SearchRequest searchRequest = new SearchRequest();
        searchRequest.setBaseDn(baseDn);
        searchRequest.setReturnAttributes(returnAttributes);
        searchRequest.setSearchScope(searchScope);
        searchRequest.setSearchFilter(filter);

        final SearchOperation searchOperation = new SearchOperation(conn);
        SearchResult searchResult = searchOperation.execute(searchRequest).getResult();

        return searchResult.getEntries();
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
