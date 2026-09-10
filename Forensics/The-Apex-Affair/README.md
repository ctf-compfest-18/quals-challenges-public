# The Apex Affair

by jay

---

## Flag

```
COMPFEST18{th1s_chall3nges_was_cool_right?_<ran_16>}
```

## Description
Northbridge Industrial Solutions is preparing a confidential acquisition when Finance Manager Edward reports that the important document on his workstation have become inaccessible. Initial findings suggest the compromise may have begun with a phishing message, but investigators have not identified which one or how it led to the attack. Analyze the provided forensic artifacts, trace the intrusion, and uncover what happened before the files were encrypted.

password:2cf4554ee26f34801adfbecc222a797e02b179f9887940e1c0343771853d263b

attachment link1 :https://drive.google.com/file/d/1X-VjdqFVMVZFcpWTcmwqsXL9nFHv6GKx/view?usp=sharing  
attachment alternative_link2 : https://drive.google.com/file/d/1HSJD5rGagEZ61H64r1BoO2f6FwAvYfdH/view?usp=sharing  
attachment alternative_link3 : https://drive.google.com/file/d/1cOPTbNEGWydL1gSHoxe4yJZBcC7uHhJL/view?usp=sharing  
sha256: e1815c3414234c690e5b3fa73dd4cc74c0730b7e5a675d28da198888527625e2

## Difficulty
very hard

## Hints
* no hint

## Tags
phishing, disk forensic, memory forensic, file carving, malware analysis

## Deployment
Penjelasan cara menjalankan service yang dibutuhkan serta requirementsnya.

Salin `challenge.example.yml` menjadi `challenge.yml` dan lengkapi semua
metadata sebelum membuka pull request. Format dan mode deployment dijelaskan di
[`docs/challenge-deployment.md`](../docs/challenge-deployment.md).

- Install docker engine>=19.03.12 and docker-compose>=1.26.2.
- Run the container using:
    ```
    docker-compose up --build --detach
    ```