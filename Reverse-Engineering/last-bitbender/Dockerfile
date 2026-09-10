FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends socat && rm -rf /var/lib/apt/lists/*
RUN useradd -m ctf
WORKDIR /app
COPY server.py core.py ./
EXPOSE 36769
USER ctf
CMD ["sh","-c","test -n \"$FLAG\" || { echo 'FLAG is required' >&2; exit 1; }; exec socat -T60 TCP-LISTEN:36769,reuseaddr,fork 'EXEC:python3 -u server.py,stderr'"]
