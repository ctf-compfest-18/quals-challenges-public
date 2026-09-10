# Writeup piyakcrypt

- option [2] membocorkan 40 bit teratas dari private key (tag),  
- option [5] mengekspos output Mersenne Twister yang ter-obfuscate secara invertibel sehingga state RNG bisa di-clone sempurna,  
- option [3] menggunakan nonce ECDSA k yang 128 bit teratasnya berasal dari MT (dapat diprediksi) sementara 128 bit sisanya baru benar-benar random.

Kumpulkan 624 nilai panel (8x option 5) → invert transformnya → clone state MT → request semua signature sambil prediksi high-bits nonce k dari MT clone → susun lattice HNP (Hidden Number Problem) berdasarkan bias nonce + prefix key yang bocor → reduksi dengan LLL → dapatkan 216 bit bawah private key → rekonstruksi secret = tag << 216 | piece → submit via option 6 → flag.