FROM ghcr.io/kawijayaa/questionnaire@sha256:837d0f6816d067e38f502b1ac4bf8ff2a382d86f7c949e74a887bda5645bdab5
USER root
RUN touch /flag.txt && chown questionnaire:questionnaire /flag.txt && chmod 600 /flag.txt
USER questionnaire
COPY /src/config.yaml /app/config.yaml
COPY /src/tcp_server.py /app/tcp_server.py
EXPOSE 1337
CMD ["python", "/app/tcp_server.py"]
