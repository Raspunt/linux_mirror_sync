# JazzySync — Linux Mirror Sync

A CLI tool (`jazzy`) for managing local Linux distribution mirrors. Supports **Arch Linux**, **Debian**, and **Fedora** with intelligent sync, integrity verification, and repair.

---

## Features

- **Unified interface** for multiple distributions
- **Smart sync** — Arch checks `lastupdate` before syncing; Debian uses `debmirror`
- **Integrity verification** — parallel checking with progress (`zstd` for Arch, `dpkg-deb` for Debian)
- **Automatic repair** — `fix` removes corrupt packages and re-downloads them
- **Repository whitelist** — sync only the repositories you need
- **Portable distribution** — bundled JRE via `jlink`; no Java installation required on target systems

---

## System Requirements

### Runtime dependencies

| Mirror | Required tools |
|--------|---------------|
| **Arch Linux** | `rsync` (zstd optional for verify) |
| **Debian** | `rsync` (dpkg-deb/ar optional for verify) |
| **Fedora** | `rsync` (rpm optional for verify) |

Install on Arch-based systems:
```bash
sudo pacman -S rsync
```

Install on Debian-based systems:
```bash
sudo apt install rsync
```

### Build dependencies (only if building from source)

- **JDK 21** or newer
- **Maven 3.9** or newer

---

## Installation

### Option 1: Build from source

```bash
git clone <repository-url>
cd JazzySync
make dist
./dist/jazzy list
```

The `make dist` command:
1. Compiles the project with Maven
2. Analyzes required JDK modules via `jdeps`
3. Builds a minimal bundled JRE (~40 MB) via `jlink`
4. Creates a portable `dist/` directory

### Option 2: Download pre-built release

Download `jazzysync-linux-x64.tar.gz` from the [Releases](https://github.com/.../releases) page:

```bash
tar xzf jazzysync-linux-x64.tar.gz
cd jazzysync
./jazzy sync
```

> **Note:** The bundled JRE is built for **x86_64 (amd64)** Linux. For ARM or other architectures you must build from source on the target machine.

---

## Configuration

Configuration file: `~/.config/jazzy/config.json`

Created automatically with sensible defaults on the first run.

### Example configuration

```json
{
  "baseUrl": "rsync://mirror.yandex.ru/",
  "targetDir": "/mnt/big/mirrors/",
  "logDir": "~/.cache/jazzy",
  "distros": {
    "arch": {
      "sourcePath": "archlinux/",
      "enabled": true,
      "repos": ["core", "extra", "multilib"]
    },
    "debian": {
      "sourcePath": "debian/",
      "enabled": true,
      "sourceHost": "mirror.yandex.ru",
      "sourceRoot": "/debian",
      "dist": "trixie",
      "arch": "amd64",
      "repos": ["main", "contrib", "non-free", "non-free-firmware"]
    }
  }
}
```

### Configuration fields

| Field | Description | Default |
|-------|-------------|---------|
| `baseUrl` | Base rsync URL for all mirrors | `rsync://mirror.yandex.ru/` |
| `targetDir` | Local root directory for mirrors | `~/mirrors` |
| `logDir` | Directory for log files | `~/.cache/jazzy` |

#### Per-distribution settings (`distros.<name>`)

| Field | Description | Applies to |
|-------|-------------|------------|
| `sourcePath` | Path on the rsync server | All |
| `enabled` | Enable or disable this distribution | All |
| `repos` | Whitelist of repositories/sections | All (Arch: repo names; Debian: overrides `section`) |
| `sourceHost` | Debian mirror hostname | Debian only |
| `sourceRoot` | Debian mirror root path on server | Debian only |
| `dist` | Debian release codename | Debian only |
| `arch` | Debian architecture | Debian only |
| `section` | Debian sections (comma-separated) | Debian only (fallback if `repos` not set) |

### Arch repository whitelist

Use `repos` to sync only specific repositories:

```json
"arch": {
  "repos": ["core", "extra", "multilib"]
}
```

Available repositories in a typical Arch mirror:
`core`, `extra`, `multilib`, `core-testing`, `extra-testing`, `multilib-testing`, `gnome-unstable`, `kde-unstable`

The `pool/` directory and metadata files (`lastupdate`, `lastsync`) are always synced regardless of the whitelist.

### Debian section whitelist

Use `repos` to sync only specific sections:

```json
"debian": {
  "repos": ["main", "contrib"]
}
```

This overrides the `section` field passed to `debmirror`.

---

## CLI Usage

```
Usage: jazzy [-hV] [-t=<targetDir>] <COMMAND> [TARGET]

Arguments:
  <COMMAND>  Command to execute: sync, verify, check, fix, status, list
  [TARGET]   Distribution to process: arch, debian, fedora, or all (default: all)

Options:
  -t, --target-dir=<targetDir>  Override target directory for mirrors
  -h, --help                    Show help
  -V, --version                 Show version
```

### Commands

| Command | Description | Safe? |
|---------|-------------|-------|
| `sync` | Synchronize mirror(s) with upstream | ⚠️ Modifies files |
| `check` | Dry-run; show what would change | ✅ Read-only |
| `verify` | Verify integrity of local packages (if tools available) | ✅ Read-only |
| `fix` | Remove corrupt packages and re-download | ⚠️ Deletes bad files |
| `status` | Show mirror status, size, last sync | ✅ Read-only |
| `list` | List configured distributions | ✅ Read-only |

### Examples

```bash
# Sync all enabled mirrors
jazzy sync

# Sync only Arch Linux
jazzy sync arch

# Check all mirrors for updates (dry-run)
jazzy check

# Verify integrity of Debian packages
jazzy verify debian

# Fix corrupt Arch packages
jazzy fix arch

# Show status table
jazzy status

# List distributions
jazzy list
```

---

## Makefile Targets

| Target | Description |
|--------|-------------|
| `make all` | Build fat-jar (default) |
| `make dist` | Build portable distribution with bundled JRE |
| `make package` | Build jar only |
| `make compile` | Compile sources |
| `make test` | Run tests |
| `make clean` | Clean build artifacts |
| `make verify` | Full verification (clean + test + package) |

---

## Logs

All operations are logged to both **stdout** and a log file:

```
~/.cache/jazzy/mirror-sync.log
```

Each mirror also maintains its own sync logs in the mirror directory:

```
/mnt/big/mirrors/archlinux/.logs/sync-YYYYMMDD-HHMMSS.log
```

---

## Architecture

```
IMirror (interface)
├── ArchMirror    → rsync (zstd optional)
├── DebianMirror  → rsync (debmirror/dpkg-deb optional)
└── FedoraMirror  → rsync (rpm optional)
```

- **Java acts as an orchestrator** — it launches external tools (`rsync`, `debmirror`, `zstd`, `dpkg-deb`) via `ProcessBuilder`
- **Parallel verification** — uses `ExecutorService` with all available CPU cores
- **Template method pattern** — `AbstractMirror` defines the `getStatus()` algorithm; subclasses provide only distribution-specific details

---

## License

MIT
