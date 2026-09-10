# piyakcrypt

by fele

---

## Flag

```
COMPFEST18{b1as3d_n0nc3_mt_r3c0v3ry_lll_hnp_go_brr}

```

## Description
piyak... piyak... piyak... do you know what sound that is?


## Difficulty
Tingkat kesulitan soal: medium

## Hints
* hint 1
* hint 2
* hint dst.

## Tags
hnp, lattice, ecdsa, mt1997

## Deployment
Penjelasan cara menjalankan service yang dibutuhkan serta requirementsnya.

#### Contoh 1
- Install docker engine>=19.03.12 and docker-compose>=1.26.2.
- Run the container using:
    ```
    docker-compose up --build --detach
    ```

#### Contoh 2
- How to compile:
    ```
    gcc soal.c -o soal -O2 -D\_FORTIFY\_SOURCE=2 -fstack-protector-all -Wl,-z,now,-z,relro -Wall -no-pie
    ```
- Jalankan:
    ```
    ./soal
    ```
- Workdir di `/home/...`
- Gunakan libc 2.31 ketika sudah keluar. Alias Ubuntu 20.04.

## Notes
Tambahan informasi untuk soal, deployment, atau serangan yang mungkin terjadi pada service soal
