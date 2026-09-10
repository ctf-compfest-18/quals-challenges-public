#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VERSION=$1

for IMAGE in cairo eth solana sui; do
  sudo docker tag xxymboll/$IMAGE:latest xxymboll/$IMAGE:$VERSION
  sudo docker push xxymboll/$IMAGE:$VERSION
  sudo docker push xxymboll/$IMAGE:latest
  echo "Pushed xxymboll/$IMAGE:latest and :$VERSION"
done
