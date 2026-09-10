# Writeup — The 67th Line

## Bagian 1: Instagram

Titik awal challenge adalah akun `@kuliah67.archive`.

Tiga caption terdiri dari baris-baris yang semuanya diawali huruf `B` atau `O`.
Hashtag Boston dan petunjuk tentang awal baris mengarah ke Baconian cipher. Saya
memakai pemetaan:

```text
B = 0
O = 1
```

Bit dibaca per lima dengan `00000 = A`, `00001 = B`, dan seterusnya.

Hasil masing-masing post:

```text
Post 1: RIST
Post 2: EKASTE
Post 3: RGATE
```

Jika digabung:

```text
RISTEKASTERGATE
```

Artinya file berikutnya berada di:

```text
https://ristek.link/astergate
```

## Bagian 2: ASTERGATE

Arsip berisi:

```text
chall.py
records.bin
records.json
sealed.json
```

Cipher bekerja pada block 12 byte. Tiga round awal memakai fungsi kuadratik,
XOR round key, dan permutasi bit. Setiap byte output kemudian melewati encoding
kuadratik dan matriks biner 8×8 yang dipilih dari 4096 kemungkinan.

Derajat aljabar tiga round internal tidak lebih dari delapan. Karena itu XOR
nilai fungsi pada affine space berdimensi lebih dari delapan akan hilang.
Structure dengan dimensi tujuh dan delapan tidak dipakai.

Untuk tiap posisi byte, distribusi output diubah menjadi persamaan pada ruang
monomial Boolean berderajat paling tinggi dua. Dimensi ruangnya:

```text
1 + 8 + C(8, 2) = 37
```

Persamaan dari seluruh structure valid menghasilkan cancellation space. Setelah
itu semua 4096 kandidat matriks dicoba. Inverse encoding setiap kandidat diubah
ke ANF dengan Möbius transform. Kandidat diterima hanya jika seluruh fungsi
koordinatnya berada pada cancellation space yang ditemukan. Setiap posisi byte
menyisakan tepat satu index matriks.

Translation byte tidak muncul pada full-cube sum. Setelah index matriks didapat,
translation bisa dihitung dari known pair yang sudah tersedia pada structure:

```text
translation = captured XOR predicted
```

Dua belas komponen key kemudian dipakai untuk membuka `sealed.json`. Plaintext
yang diperoleh adalah 32 byte acak, sehingga flag dibentuk sebagai:

```text
COMPFEST18{plaintext.hex()}
```

Jalankan solver dari root repository:

```bash
python3 writeup/solve.py
```
