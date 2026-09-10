## Author Notes
- Udah coba konsep chall yg beda-beda, jadi final gw ttp pilih concept yang ini, for the sake of authenticity dan ga semata-mata biar not one-shotted. Benchmarking with Opus 5: 6 menit (file upload claude web), GPT 5.6 sol: 6 menit (file upload chatgpt web)
- Reference: https://black-hat-zig.cx330.tw/Advanced-Malware-Techniques/Process-Injection/Heavens-Gate/heavens_gate/#remotethreadasm

## Solve Path
Overall gua ga main di fake functions atau redherring, tp challengenya lebih ke staged decryption dan mode nya pindah-pindah (x86-32 to x86-64 to x86-32 to x86-64 to x86-32)
- Analyze PE32, cari blob 0x2a2 byte di .data
- Extract blob dari 0x402000
- Cek heaven's gate di CS dan retf
- Disass tiap stage pakai mode x86-32 atau x86-64
- Decrypt next stage dengan keystream dari stage sebelumnya
- Recover dan reimplement transform 16-byte request jadi 16-byte response
- Validasi dlu dengan fixed self-test (validasi buat player sblm connect nc apakah reimplementasinya dah bener atau ga)
- Connect ke nc, hitung response untuk nonce baru, baru flag

## Additional Notes
Kalau mau solve pake ghidra, di sini ghidra import binary sebagai PE32, nah jadi bagian x86-64 ga otomatis di-disass sesuai modenya (misal mode 32, yg 64 ke-disass pake mode 32, jadi hasil disassnya kaya broken). Encrypted stagenya juga masi ciphertext jadi belum keliatan kaya code. Jadi caranya bisa:
- decrypt blob dulu, terus analyze terpisah: 
  - x86-32: 0x000, 0x0d4, 0x230
  - x86-64: 0x017, 0x196
