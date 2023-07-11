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
public class NoneProvider implements CertificateProvider {

    private final CertificateMetadata certificateMetadata;
    public NoneProvider(CertificateMetadata certificateMetadata) {
        this.certificateMetadata = certificateMetadata;
    }

    @Override
    public Optional<Date> getExpirationDate() {
        return Optional.empty();
    }

    @Override
    public Flowable<JWK> privateKey() {
        throw new UnsupportedOperationException("No private key for \"none\" algorithm");
    }

    @Override
    public Single<Key> key() {
        throw new UnsupportedOperationException("No key for \"none\" algorithm");
    }

    @Override
    public Single<String> publicKey() {
        throw new UnsupportedOperationException("No public key for \"none\" algorithm");
    }

    @Override
    public Flowable<JWK> keys() {
        throw new UnsupportedOperationException("No keys for \"none\" algorithm");
    }

    @Override
    public String signatureAlgorithm() {
        return "none";
    }

    @Override
    public CertificateMetadata certificateMetadata() {
        return certificateMetadata;
    }

    @Override
    public String toString() {
        return "NoneProvider";
    }
}
