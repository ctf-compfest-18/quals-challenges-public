FROM dimasmaualana/eth:latest

COPY deploy/ /home/ctf/
COPY compiled/Setup.sol/Setup.json /home/ctf/compiled/Setup.sol/Setup.json

RUN cd /home/ctf \
    && pip install -r requirements.txt --break-system-packages \
    && test -s compiled/Setup.sol/Setup.json
