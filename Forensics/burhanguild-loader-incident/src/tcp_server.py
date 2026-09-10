#!/usr/bin/env python3
import os
import select
import socket
import subprocess
import sys
import threading
import time

PORT = int(os.environ.get("PORT", "1337"))
MAX_CLIENTS = max(1, int(os.environ.get("MAX_CLIENTS", "16")))
IDLE_TIMEOUT = max(5, int(os.environ.get("IDLE_TIMEOUT", "90")))
MAX_INPUT = max(512, int(os.environ.get("MAX_INPUT", "4096")))
CLIENT_SLOTS = threading.BoundedSemaphore(MAX_CLIENTS)
QUESTIONNAIRE_SERVER = os.environ.get("QUESTIONNAIRE_SERVER", "/app/server.py")
QUESTIONNAIRE_CONFIG = os.environ.get("QUESTIONNAIRE_CONFIG", "/app/config.yaml")

if not os.environ.get("FLAG"):
    raise RuntimeError("FLAG environment variable must be set")


def stop_process(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            process.kill()
            try:
                process.wait(timeout=1)
            except subprocess.TimeoutExpired:
                pass


def handle(connection):
    process = None
    try:
        environment = os.environ.copy()
        environment["PYTHONWARNINGS"] = "ignore::DeprecationWarning"
        process = subprocess.Popen(
            [sys.executable, "-u", QUESTIONNAIRE_SERVER, "-c", QUESTIONNAIRE_CONFIG],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            bufsize=0,
            env=environment,
        )
        last_activity = time.monotonic()
        total_input = 0

        def feed():
            nonlocal last_activity, total_input
            try:
                while process.poll() is None:
                    if time.monotonic() - last_activity > IDLE_TIMEOUT:
                        stop_process(process)
                        break
                    readable, _, _ = select.select([connection], [], [], 0.25)
                    if not readable:
                        continue
                    data = connection.recv(1024)
                    if not data:
                        break
                    total_input += len(data)
                    if total_input > MAX_INPUT:
                        stop_process(process)
                        break
                    last_activity = time.monotonic()
                    process.stdin.write(data)
                    process.stdin.flush()
            except (BrokenPipeError, ConnectionError, OSError):
                pass
            finally:
                try:
                    process.stdin.close()
                except (BrokenPipeError, OSError):
                    pass

        feeder = threading.Thread(target=feed, daemon=True)
        feeder.start()
        while True:
            data = process.stdout.read(4096)
            if not data:
                break
            connection.sendall(data)
        feeder.join(timeout=1)
    except (BrokenPipeError, ConnectionError, OSError):
        pass
    finally:
        if process is not None:
            stop_process(process)
        try:
            connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        connection.close()
        CLIENT_SLOTS.release()


server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(("0.0.0.0", PORT))
server.listen(MAX_CLIENTS)
print(f"listening on {PORT}", flush=True)

while True:
    client, _ = server.accept()
    if not CLIENT_SLOTS.acquire(blocking=False):
        client.close()
        continue
    threading.Thread(target=handle, args=(client,), daemon=True).start()
