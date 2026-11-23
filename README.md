# OpenHomeo Workstation (OpenSource FOSS Edition)
### Next-Generation Classical Homeopathic Clinical Workstation & SIMD Repertorization Engine

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Java: 21 LTS](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Rust: SIMD Core](https://img.shields.io/badge/Rust-Native%20SIMD-red.svg)](https://www.rust-lang.org/)
[![Panama FFM](https://img.shields.io/badge/FFM-Project%20Panama-purple.svg)](https://openjdk.org/jeps/454)
[![Platform: Win | Mac | Linux](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-brightgreen.svg)]()
[![Free: FOSS](https://img.shields.io/badge/Status-100%25%20Free%20%26%20Open%20Source-teal.svg)]()

---

OpenHomeo is a modern, high-performance, privacy-first desktop workstation designed to **break the corporate monopoly** in homeopathic software. While legacy platforms (RadarOpus, Hompath Zomeo, MacRepertory) charge ₹1,50,000 to ₹3,20,000 for outdated 32-bit binaries locked behind physical USB dongles, **OpenHomeo delivers a sub-millisecond Rust SIMD engine, 1,217 verified pharmacopoeial remedies, full Indian pharmacy stock management, bedside mobile companion, and cryptographic Google Drive synchronization — 100% free forever.**

---

## ⚡ Instant 1-Line Universal Installers

### Windows 10 / 11 (PowerShell)
Open PowerShell (as standard user or admin) and execute:
```powershell
irm https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/install.ps1 | iex
```
*Auto-detects architecture, verifies/installs OpenJDK 21 LTS, builds native acceleration library, and creates an OpenHomeo Workstation desktop shortcut.*

---

### macOS (Apple Silicon M1/M2/M3/M4 & Intel Core) / Linux
Open Terminal and execute:
```bash
curl -fsSL https://raw.githubusercontent.com/bkoustoov-oss/openhomeo/master/install.sh | bash
```
*Installs via Homebrew / apt, compiles native SIMD core, and sets up the `openhomeo` terminal command.*

---

## 🌟 Key Features

| Capability | Technical Implementation | Clinical Advantage |
| :--- | :--- | :--- |
| **Rust SIMD Engine** | Bitwise AVX-512 / NEON vector scoring via Java 22+ Panama FFM | Sub-millisecond repertorization across 500,000+ rubrics and 5 concurrent clipboards |
| **1,217 Master Remedies** | Latin binomials, Indian common names (*Ashwagandha, Kalmegh, Tulsi, Kurchi, Gurmar*) | Complete active phytochemical constituents, mode of action, and organ affinity |
| **Classical Rhymes** | Materia Medica pedagogical mnemonic verses | Memorization and rapid differential remedy cross-comparison |
| **Hahnemannian L-S-M-C** | Location, Sensation, Modality, Concomitants + Constitution + Miasm | Structured constitutional case-taking with longitudinal Hering's Law evaluation |
| **Scale-Aware Potency** | Centesimal (30 to CM), Decimal (Q to 200X), Fifty-Millesimal (LM 0/1 to LM 0/30) | Prevents potency typos and automates posology repetition |
| **Dual-Counter Billing** | Doctor Desk / Reception (`DOCTOR_DESK_PAID`) vs Dispensary Counter (`COLLECT_BOTH`) | Seamless alignment with Indian clinic and hospital cashier workflows |
| **Pharmacy Stock Register** | SBL, Schwabe India, Dr. Reckeweg, Bakson, Medisynth (Dilutions, Q, Biochemic, Creams) | Multi-medicine batch checklists, low-stock triggers, volume reorder requests |
| **Bedside Mobile Companion** | Java Virtual-Thread micro-server on LAN (`:8090`) with PIN RBAC | Doctors examine inpatients and record bedside cases from any smartphone or tablet |
| **Google Drive Sync** | Timestamped `.ohbak` (ZIP) packaging + SQLite `PRAGMA integrity_check;` + SHA-256 / MD5 | Automated verification and byte-exact cloud backup to Google Drive |
| **CIE-L\*a\*b\* Dermascopy** | Objective colorimetric delta-E skin lesion and wound healing tracker | Eliminates subjective visual guesswork in dermatological follow-ups |
| **Non-Destructive OTA Updates** | Master repertory patcher isolated from `user_clinic.db` | Updates knowledge base and provings without ever touching patient EMR files |

---

## 🏛️ System Architecture

```
+--------------------------------------------------------------------------------+
|                        OpenHomeo Workstation Architecture                      |
+--------------------------------------------------------------------------------+
|  Presentation:  JavaFX 21 + AtlantaFX Modern Dark/Light Design System           |
|  EMR & Flow:    Hahnemannian L-S-M-C • Prescription Engine • Posology Billing  |
|  AI Reasoner:   Hahnemannian/Kentian Posology Logic • Admin Report of Faith     |
|  Bedside:       Embedded HTTP Virtual-Thread Server (PIN RBAC, Mobile/Tablet)  |
|  Backup/Sync:   SHA-256 Cryptographic Archives (.ohbak) • Google Drive Sync    |
+--------------------------------------------------------------------------------+
|  Interop Layer: Java 22+ Project Panama Foreign Function & Memory API (FFM)    |
+--------------------------------------------------------------------------------+
|  Native Core:   Rust SIMD Micro-Engine (AVX-512 / NEON bitset rubric scoring)   |
+--------------------------------------------------------------------------------+
|  Persistence:   master_repertory.db (Read-Only) • user_clinic.db (AES-256-GCM) |
+--------------------------------------------------------------------------------+
```

---

## 🛡️ Privacy & Confidentiality Guarantee

OpenHomeo is engineered on the principle of **Zero-Knowledge Local Sovereignty**:
- **Zero Cloud Leakage**: Patient names, addresses, phone numbers, and case complaints reside strictly in your local encrypted `user_clinic.db` (SQLCipher AES-256-GCM).
- **Offline First**: All repertorizations, Materia Medica searches, and posology evaluations execute locally on your machine without requiring an active internet connection.
- **Git Privacy**: The repository's `.gitignore` strictly bans committing any clinic database files, backup archives (`.ohbak`), clinical photos (`vault/`), or doctor keys.

---

## 💻 Building from Source

### Prerequisites
- **JDK 21 LTS** or newer (Eclipse Temurin or Microsoft OpenJDK recommended)
- **Apache Maven 3.9+**
- **Rust & Cargo** (latest stable)

### Build Commands
```bash
# 1. Clone repository
git clone https://github.com/bkoustoov-oss/openhomeo.git
cd openhomeo

# 2. Build native Rust acceleration library
cd native-core
cargo build --release
cd ..

# 3. Build Java modules
mvn clean package -DskipTests

# 4. Launch Desktop Workstation
mvn javafx:run -pl openhomeo-gui
```

---

## 📱 Default Bedside Companion Credentials (RBAC)
When running OpenHomeo, the embedded mobile server launches automatically on port `8090`:
- **Doctor PIN**: `doctor123` (Full patient case-taking, rubric review, and bedside posology)
- **Admin PIN**: `admin123` (Full administrative privileges, faith dossier, network settings)
- **Pharmacist PIN**: `pharma123` (Stock lookup and patient demographics; clinical editing restricted)

---

## 📄 License & Open-Source Philosophy

OpenHomeo is proudly licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. 

### Why AGPL-3.0?
Commercial proprietary medical software vendors have a history of taking open-source tools, rebranding them, locking them behind expensive paywalls, and depriving practitioners of the source code. Under AGPL-3.0:
- Anyone may run, distribute, modify, and improve OpenHomeo freely.
- Any entity that modifies OpenHomeo or deploys it as a network service **must release their complete modifications under the same open-source AGPL-3.0 license**.
- Homeopathy remains forever free, democratic, and accessible to every practitioner, researcher, and student worldwide.

*For detailed clinical product documentation, please see [PRODUCT_BROCHURE.md](PRODUCT_BROCHURE.md).*
