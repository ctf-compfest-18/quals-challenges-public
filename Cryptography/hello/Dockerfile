FROM sagemath/sagemath:latest

ENTRYPOINT []

USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends socat && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app && chown sage:sage /app
COPY --chown=sage:sage src/src.sage /app/src.sage

ENV DOT_SAGE=/tmp/.sage \
    TMPDIR=/tmp

USER sage
EXPOSE 1337

CMD ["socat", "TCP-LISTEN:1337,fork,reuseaddr", "EXEC:sage /app/src.sage,stderr"]