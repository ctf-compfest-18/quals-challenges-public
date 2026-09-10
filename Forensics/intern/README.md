# Int*rn

by Karev

---

## Flag

```
COMPFEST18{flag_sha256(flag)[:10]}
```

## Description
A company's employee recently fell victim to a ransomware attack. The company has hired you and your firm to find out what happened. After hearing the details of the attack, you realized that it sounded familiar to another attack that happened a few months back. The police managed to arrest one of the criminal responsible for it but they failed to arrest his partners. Luckily you know someone in the police department so you asked your intern to gather evidence from the victim's computer while you meet your friend to see if he could give you any evidence they got from the captured criminal's computer. After collecting the evidence, you realized that your intern has done a teribble job at acquisition and missed out a lot of potentially important forensics artifacts. Could you still find out what happened?. 

Download the artifacts from : https://drive.google.com/file/d/1uZKu7_eFSJxJb9RJfj94bchoDHzyPkck/view?usp=sharing

Password: sEzXzWyqRt95ASRH

Notes:
1. All malwares found in this challenge are working malware. DO NOT RUN IT ON YOUR HOST COMPUTER, USE A SANDBOX/VM. I am not responsible for any damages on your computer.
2. {12b27ea2-0101-4435-a4af-5a8743ce345f} is the evidence from the criminal's computer while {f1733278-c744-4bf0-9b9a-b1dfb278f4bf} is from the victim's computer
3. Unless specified otherwise, for questions needing multiple answers, the order of the answer you provide does not matter.

## Difficulty
hard - insane

## Hints
* hint 1
* hint 2
* hint dst.

## Tags
Tags dari soal pisahkan koma (e.g: tags1, tags2, tags3)

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