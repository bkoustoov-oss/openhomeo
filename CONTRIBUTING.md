# Contributing to OpenHomeo Workstation

Thank you for your interest in contributing to **OpenHomeo**, the free and open-source classical homeopathy workstation! OpenHomeo is dedicated to Hahnemannian fidelity, computational excellence, and the democratization of homeopathic medical software.

---

## How You Can Contribute

### 1. 🐞 Reporting Bugs
If you encounter an issue or error while running OpenHomeo:
1. Search the [GitHub Issues](https://github.com/bkoustoov-oss/openhomeo/issues) tab to ensure the issue hasn't already been reported.
2. If not, open a new issue using our **[Bug Report Template](https://github.com/bkoustoov-oss/openhomeo/issues/new?template=bug_report.md)**.
3. Include your Operating System (Windows 10/11, macOS, Linux), Java version (`java -version`), and any relevant log output from `~/.openhomeo/logs/`.
4. **Never paste confidential patient records or personal health information (PHI) in bug reports.**

### 2. 💡 Suggesting Clinical & Workflow Modifications
Have an idea for improving case-taking, repertorization, posology logic, or pharmacy inventory management?
1. Open a new issue using our **[Feature Request / Modification Template](https://github.com/bkoustoov-oss/openhomeo/issues/new?template=feature_request.md)**.
2. Describe the clinical scenario, the classical rationale (referencing Hahnemann, Kent, Boenninghausen, or Boger), and how the modification improves practitioner workflow.

### 3. 🧪 Submitting Community Proving Data & Materia Medica
We actively encourage proving research organizations, colleges, and physicians to submit verified proving monographs:
1. Open an issue using the **[Proving Submission Template](https://github.com/bkoustoov-oss/openhomeo/issues/new?template=proving_submission.md)**.
2. Provide the standardized proving JSON or CSV export adhering to the schema documented in `openhomeo-core/materia_medica`.
3. Ensure classical verification details: provers count, potencies used, control trials, and key symptom rubrics.

### 4. 💬 Community Discussions & Clinical Case Debates
Join the [GitHub Discussions](https://github.com/bkoustoov-oss/openhomeo/discussions) to discuss:
- Complex clinical case analyses.
- Materia Medica comparisons.
- High-potency vs LM posology experiences.
- Feature requests and roadmap planning.

---

## Development Setup

### Prerequisites
- **JDK 21 LTS** or newer
- **Apache Maven 3.9+**
- **Rust & Cargo** (for the SIMD acceleration library)

### Building the Project
```bash
# 1. Clone your fork
git clone https://github.com/<your-username>/openhomeo.git
cd openhomeo

# 2. Build native Rust core
cd native-core
cargo build --release
cd ..

# 3. Build and test Java modules
mvn clean test

# 4. Launch GUI workstation
mvn javafx:run -pl openhomeo-gui
```

---

## Code of Conduct & Medical Ethics

OpenHomeo is developed to support ethical, classical homeopathic practice:
- Maintain strict respect for patient confidentiality and zero-knowledge privacy.
- Ensure all algorithm changes maintain faithfulness to classical Hahnemannian principles.
- Be respectful, constructive, and collaborative in all community interactions.
