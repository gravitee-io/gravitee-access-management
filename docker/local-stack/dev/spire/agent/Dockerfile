# Shell-capable wrapper around the distroless spire-agent image so the
# entrypoint can read the bootstrap-generated join token from the shared
# volume and pass it via -joinToken at startup.
FROM ghcr.io/spiffe/spire-agent:1.9.6 AS spire
FROM alpine:3.19
RUN apk add --no-cache bash
COPY --from=spire /opt/spire/bin/spire-agent /usr/local/bin/spire-agent
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
