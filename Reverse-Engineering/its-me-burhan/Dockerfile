FROM eclipse-temurin@sha256:068a8f9ae4b74d9a20de3ecca771ba1a6437f1d7a8a8ad6deaf9dbdd2274397a

RUN apt-get update && apt-get install -y --no-install-recommends socat && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY src/ /app/src/
RUN sed -i 's/\r$//' /app/src/infra/*.sh && chmod +x /app/src/infra/*.sh
RUN javac -d /app/out $(find /app/src -name '*.java' -not -path '*/test/*')

ENV BURHAN_OUT=/app/out PORT=30067
EXPOSE 30067

ENTRYPOINT ["bash","/app/src/infra/entrypoint.sh"]
