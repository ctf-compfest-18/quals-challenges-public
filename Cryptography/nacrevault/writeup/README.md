# Writeup — NACREVAULT

Public archive berisi tiga file:

```text
chall.py
transcript.json
sample.hex
```

`chall.py` hanya menyediakan relasi yang harus dipenuhi oleh sebuah state
ternary. Secret mempunyai panjang 192 dan setiap koordinat berada di
`{-1, 0, 1}`.

Ada dua transcript yang menggunakan secret yang sama:

1. relasi exact modulo 17 dari `transcript.json`;
2. relasi negacyclic noisy modulo 4093 dari `sample.hex`.

## 1. Mengelompokkan kolom transcript exact

Matriks `M` mempunyai 192 kolom. Kolom-kolomnya tidak acak penuh. Delapan kolom
tertentu selalu berada pada subspace berdimensi dua yang sama. Dengan memeriksa
span setiap pasangan kolom, semua kolom dapat dipisahkan menjadi 24 kelompok
berukuran delapan.

Urutan kelompok dan urutan kolom di dalam kelompok sudah diacak, jadi batas
kelompok tidak bisa dibaca langsung dari index.

## 2. Memisahkan target ke tiap kelompok

Dari setiap kelompok dipilih dua kolom independen. Seluruh 48 kolom basis
membentuk matriks persegi 48×48 modulo 17. Sistem ini diselesaikan dengan
eliminasi Gaussian untuk menulis target sebagai jumlah 24 komponen, masing-masing
berasal dari satu kelompok.

Pada satu kelompok, secret lokal selalu mempunyai:

```text
2 buah +1
2 buah -1
4 buah 0
```

Jumlah pola lokalnya:

```text
C(8, 2) × C(6, 2) = 420
```

Semua 420 pola dicoba terhadap komponen kelompoknya. Transcript exact biasanya
masih menyisakan beberapa kandidat pada sebagian kelompok.

## 3. Memakai transcript negacyclic

`sample.hex` memuat seed, salt, dan noisy target. Seed dipakai oleh SHAKE-256
untuk membentuk vector ring. Dari vector tersebut dibuat matriks negacyclic pada:

```text
Z_4093[x] / (x^192 + 1)
```

Setiap kandidat lokal mempunyai kontribusi terhadap target ring. Daftar
kelompok dibagi menjadi dua bagian yang ukuran ruang pencariannya seimbang.
Kontribusi bagian kiri dimasukkan ke bucket, lalu bagian kanan dicocokkan dengan
target menggunakan tiga koordinat awal sebagai filter.

Kandidat yang lolos filter diuji pada seluruh 192 koordinat. Residual yang benar
harus memenuhi batas error dari `chall.py`.

## 4. Membentuk flag

Secret yang ditemukan divalidasi sekali lagi menggunakan `verify()`. Flag bukan
plaintext yang dienkripsi. Nilainya dihitung dari secret dan salt:

```python
encoded = bytes(value + 1 for value in secret)
digest = sha256(b"NACREVAULT/V3/flag/" + salt + encoded).hexdigest()
```

Kemudian:

```text
COMPFEST18{digest}
```

Jalankan solver dari root repository:

```bash
python3 writeup/solve.py
```
