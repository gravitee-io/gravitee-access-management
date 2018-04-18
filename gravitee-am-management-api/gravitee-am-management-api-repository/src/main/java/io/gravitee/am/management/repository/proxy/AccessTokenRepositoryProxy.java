package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AccessTokenRepositoryProxy extends AbstractProxy<AccessTokenRepository> implements AccessTokenRepository {

    public Maybe<AccessToken> findByToken(String token) {
        return target.findByToken(token);
    }

    public Single<AccessToken> create(AccessToken accessToken) {
        return target.create(accessToken);
    }

    public Completable delete(String token) {
        return target.delete(token);
    }

    public Observable<AccessToken> findByClientIdAndSubject(String clientId, String subject) {
        return target.findByClientIdAndSubject(clientId, subject);
    }

    public Observable<AccessToken> findByClientId(String clientId) {
        return target.findByClientId(clientId);
    }
}
