package io.gravitee.am.gateway.handler.oauth2.introspection;

import io.reactivex.Single;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IntrospectionService {

    Single<IntrospectionResponse> introspect(IntrospectionRequest request);
}
