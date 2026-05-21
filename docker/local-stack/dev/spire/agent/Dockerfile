# Shell-capable wrapper around the distroless spire-agent image so the
# entrypoint can read the bootstrap-generated join token from the shared
# volume and pass it via -joinToken at startup.
FROM ghcr.io/spiffe/spire-agent:1.9.6 AS spire
FROM alpine:3.19
RUN apk add --no-cache bash \
    && addgroup -g 1000 spire \
    && adduser -D -u 1000 -G spire spire \
    && mkdir -p /run/spire/shared /run/spire/data /run/spire-agent/public \
    && chown -R spire:spire /run/spire /run/spire-agent
COPY --from=spire /opt/spire/bin/spire-agent /usr/local/bin/spire-agent
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
USER spire
HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
    CMD ["/usr/local/bin/spire-agent", "healthcheck", "-socketPath", "/run/spire-agent/public/api.sock"]
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
