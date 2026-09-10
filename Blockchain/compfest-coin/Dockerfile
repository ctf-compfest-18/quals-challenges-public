FROM xxymboll/sui:latest

COPY src/images/challenge/src/sandbox/frontend/ /usr/lib/python/sandbox/frontend/
COPY src/images/challenge/src/sandbox/ppow.py /usr/lib/python/sandbox/ppow.py
COPY src/challenge-sui/deploy/ /home/ctf/
COPY src/challenge-sui/setup /home/ctf/setup

# Build as root so dependency cache lands in /root/.move (runtime HOME).
RUN set -eux; \
    cd /home/ctf; \
    pip install -r requirements.txt --break-system-packages; \
    rm -rf /home/ctf/setup/build /home/ctf/setup/Move.lock /home/ctf/setup/Pub.local.toml; \
    sui move --client.env testnet build --path /home/ctf/setup --default-move-flavor sui -e testnet; \
    test -d /root/.move; \
    [ -n "$(ls -A /root/.move)" ]
