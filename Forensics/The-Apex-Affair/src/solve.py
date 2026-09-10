import re
import sys

from pwn import *

# context.log_level = "debug"

FLAG_REGEX = r"COMPFEST18{.*?\}"

ANSWERS = [
    # ================= PART 1 — Initial Access =================
    "outlook",
    "2026-08-15 08:45:38",
    "Update Your Browser - Download the Latest Google Chrome",
    "2026-08-15 16:42:36",

    # ================= PART 2 — Execution and Persistence =================
    "GoogleChromeUpdater",
    'rundll32.exe "C:\\Users\\Public\\chrome_update.dll",Drop',
    "2|C:\\Program Files\\Google\\Chrome\\GoogleUpdater.exe|C:\\Program Files\\Google\\Chrome\\software_reporter_tool.exe",
    '"C:\Program Files\Google\Chrome\GoogleUpdater.exe" "add-exclusion" "Paths" "C:\Program Files\Google\Chrome"|"C:\Program Files\Google\Chrome\GoogleUpdater.exe" "secengine" "disable"|"C:\Program Files\Google\Chrome\GoogleUpdater.exe" "tp" "off"|"C:\Program Files\Google\Chrome\GoogleUpdater.exe" "rtp" "off"',
    "2026-08-15 16:48:38",
    "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options\SecurityHealthSystray.exe\Debugger=systray.exe",
    "T1547.001",
    "2026-08-15 16:49:32",

    # ================= PART 3 — Command and Control =================
    "Google Calendar",
    "18a7bdbf39f6baa148901225fa21b72cfe492720379a04f2068e1d364db65457@group.calendar.google.com",
    "Summary_description",
    "systeminfo",
    "2026-08-15 17:11:14",
    'for /f "tokens=1" %%i in (\'tasklist /nh\') do @echo %%i',
    "KEqrg7kVO1xP2BqJG9fZUpMyWblZFQWuClvZb0EYIzw=",
    "Edward Collins",

    # ================= PART 4 — Payload Execution =================
    "Notepad.exe;0x00000151C1E20000",
    "9e2413e4d0469a25c7840872f5fa98d93c4bb1c2f9a0d184f47eadaf808fefcf",

    # ================= PART 5 — Impact and Recovery =================
    "0x08FE9fc8288Cf5D5EE5f4F69c0e4f774FFA275d4",
    "0x8fe24bdb",
    "a545e0a8c675ce955431882c239e555e2de01b6bf63cd0c84514627cc306481f;7a89a98c355a84e98a3f4ef3045871d4;4096",
    "d33cc7764e98bcdef507065dc96d4b2e5fd2af0ec95d60e9b4e115fa75b2ea5e",
]

if len(sys.argv) == 3:
    io = remote(sys.argv[1], int(sys.argv[2]))
    log.info(f"Connected to remote {sys.argv[1]}:{sys.argv[2]}")
else:
    io = process(
        ["python3", "server.py"],
        env={"FLAG": "CTF{test_flag_local}"}
    )
    log.info("Started local server process")

for i, ans in enumerate(ANSWERS, 1):
    try:
        q_text_bytes = io.recvuntil(b">")
    except EOFError:
        log.failure(f"Connection closed before Question {i}")
        sys.exit(1)

    q_text = q_text_bytes.decode("utf-8", errors="ignore")

    log.info(f"Answering question {i}/{len(ANSWERS)} with: {ans!r}")
    io.sendline(ans.encode())

    try:
        resp_bytes = io.recvline()
    except EOFError:
        log.failure(f"Connection closed after sending answer for Question {i}")
        sys.exit(1)

    resp = resp_bytes.decode("utf-8", errors="ignore")

    if "INCORRECT" in resp.upper():
        log.failure(f"Answer for Question {i} was INCORRECT!")
        log.failure("Question context:")

        for line in q_text.splitlines():
            if line.strip():
                log.failure(line.strip())

        log.failure(f"Submitted answer: {ans!r}")
        sys.exit(1)

    log.success(f"Question {i} correct!")

log.info("Searching for flag in remaining output...")

try:
    output = io.recvall(timeout=3).decode("utf-8", errors="ignore")

    flag = re.search(FLAG_REGEX, output)

    if flag:
        log.success(f"Found flag: {flag.group()}")
    else:
        log.failure("Flag not found in final output!")
        print(output)

except Exception as e:
    log.error(f"Error receiving flag: {e}")