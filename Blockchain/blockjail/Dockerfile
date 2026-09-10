FROM dimasmaualana/eth:latest

COPY src/challenge-eth/deploy/ /home/ctf/
COPY src/challenge-eth/compiled/Setup.sol/Setup.json /home/ctf/compiled/Setup.sol/Setup.json

RUN cd /home/ctf \
    && pip install -r requirements.txt --break-system-packages \
    && test -s compiled/Setup.sol/Setup.json
