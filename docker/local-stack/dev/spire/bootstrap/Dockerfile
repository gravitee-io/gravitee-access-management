# Tiny shell-capable image that carries the spire-server CLI for one-shot
# bootstrap inside docker-compose. The official spire-server image is
# distroless and cannot run shell scripts.
FROM ghcr.io/spiffe/spire-server:1.9.6 AS spire
FROM alpine:3.19
RUN apk add --no-cache bash gawk coreutils \
    && addgroup -g 1000 spire \
    && adduser -D -u 1000 -G spire spire \
    && mkdir -p /run/spire/shared \
    && chown -R spire:spire /run/spire
COPY --from=spire /opt/spire/bin/spire-server /usr/local/bin/spire-server
COPY bootstrap.sh /usr/local/bin/bootstrap.sh
RUN chmod +x /usr/local/bin/bootstrap.sh
USER spire
# One-shot bootstrap; no long-running process to probe.
HEALTHCHECK NONE
ENTRYPOINT ["/bin/sh", "/usr/local/bin/bootstrap.sh"]
