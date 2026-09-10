FROM python:3.14-alpine AS builder

WORKDIR /app

COPY src/requirements.txt .
RUN pip install --user --no-cache-dir -r requirements.txt

FROM python:3.14-alpine

WORKDIR /app

RUN apk add --no-cache socat

RUN adduser -D questionnaire
COPY --from=builder /root/.local /home/questionnaire/.local
COPY src/server.py .
COPY src/config.yaml .


RUN chown -R questionnaire:questionnaire /app /home/questionnaire/.local

# Ensure the user's home is available to Python's user-site and other tools
ENV HOME=/home/questionnaire
ENV PATH=/home/questionnaire/.local/bin:$PATH

USER questionnaire

EXPOSE 1337

CMD ["sh", "-c", "socat TCP-LISTEN:1337,reuseaddr,fork EXEC:\"python -u server.py\",pty"]
