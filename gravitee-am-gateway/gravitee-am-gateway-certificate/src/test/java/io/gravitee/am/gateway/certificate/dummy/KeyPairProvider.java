package io.gravitee.am.gateway.certificate.dummy;

import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.certificate.api.Key;
import io.gravitee.am.model.jose.JWK;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.Date;
import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class KeyPairProvider implements CertificateProvider {

    private final CertificateMetadata certificateMetadata;
    private final Key certificateKey;

    public KeyPairProvider(CertificateMetadata certificateMetadata, Key certificateKey) {
        this.certificateMetadata = certificateMetadata;
        this.certificateKey = certificateKey;
    }

    @Override
    public Optional<Date> getExpirationDate() {
        return Optional.empty();
    }

    @Override
    public Flowable<JWK> privateKey() {
        return null;
    }

    @Override
    public Single<Key> key() {
        return Single.just(certificateKey);
    }

    @Override
    public Single<String> publicKey() {
        return null;
    }

    @Override
    public Flowable<JWK> keys() {
        return null;
    }

    @Override
    public String signatureAlgorithm() {
        return "ES256";
    }

    @Override
    public CertificateMetadata certificateMetadata() {
        return certificateMetadata;
    }

    @Override
    public String toString() {
        return "DefaultProvider";
    }
}
