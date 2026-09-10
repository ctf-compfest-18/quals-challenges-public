#!/bin/bash

set -e

# Plain `docker build` does not populate TARGETARCH (only buildx does), so
# Dockerfile.sui falls back to amd64 and silently fetches an x86_64 Sui binary.
# On Apple Silicon the image builds fine and then dies at runtime with
# "rosetta error: failed to open elf at /lib64/ld-linux-x86-64.so.2".
case "$(uname -m)" in
    arm64 | aarch64) TARGETARCH=arm64 ;;
    *) TARGETARCH=amd64 ;;
esac
export TARGETARCH
echo "building for TARGETARCH=$TARGETARCH"

(cd challenge-base && docker build  . -t gcr.io/paradigmxyz/ctf/base:latest)
echo "building cairo"
(cd challenge && docker build . -f Dockerfile.cairo -t xxymboll/cairo:latest)
echo "building eth"
(cd challenge && docker build . -f Dockerfile.eth -t xxymboll/eth:latest)
echo "building solana"
(cd challenge && docker build . -f Dockerfile.solana -t xxymboll/solana:latest)
echo "building sui"
(cd challenge && docker build . -f Dockerfile.sui --build-arg TARGETARCH="$TARGETARCH" -t xxymboll/sui:latest)
