# BurhanGuild Loader Incident

- Category: Forensics
- Author: PolarBear7

## Description

An internal Linux gateway was isolated after conflicting telemetry was reported.
The live-response package contains volatile captures and remnants recovered
from deleted storage. Review the collection as an incident responder, determine
which evidence belongs to the same event, and submit the final incident proof
token to the questionnaire service.

## Attachment

Participant attachment:

```text
public/attachment.zip
```

## Local Deployment

Run the service from the challenge directory:

```bash
docker compose up --build -d
```

The service listens on:

```text
nc localhost 1337
```

Stop the service with:

```bash
docker compose down
```

## Release Safety

Publish only `public/attachment.zip` as the participant attachment. The `src`
directory and questionnaire configuration are internal deployment material.
