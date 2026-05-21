# Docker Mirror Setup

This guide shows how to run a local Linux mirror server using Docker Compose. It provides both **HTTP** (for `pacman`, `apt`, `dnf`) and **rsync** (for `jazzy sync` replication between servers).

## Project structure

```
linux-mirror/
├── compose.yml      # Docker Compose: nginx + rsync daemon
├── nginx.conf       # Static file server with autoindex
└── rsyncd.conf      # Rsync module configuration
```

## Prerequisites

- Docker + Docker Compose installed
- `jazzy` installed on the server that **downloads** from the internet (see main README)
- A large disk mounted at `/mnt/big/mirrors` (or change the path in `compose.yml`)

## Quick start

### 1. Create the directory

```bash
mkdir -p ~/docker/linux-mirror
cd ~/docker/linux-mirror
```

### 2. Create `compose.yml`

```yaml
version: "3.8"

services:
  mirror:
    image: nginx:alpine
    container_name: linux-mirror
    restart: unless-stopped
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - /mnt/big/mirrors:/var/www/mirror:ro

  rsync:
    image: alpine:latest
    container_name: mirror-rsync
    restart: unless-stopped
    ports:
      - "873:873"
    volumes:
      - /mnt/big/mirrors:/data:ro
      - ./rsyncd.conf:/etc/rsyncd.conf:ro
    command: >
      sh -c "apk add --no-cache rsync &&
             rsync --daemon --no-detach --config=/etc/rsyncd.conf"
```

> Replace `/mnt/big/mirrors` with your actual mirror storage path.

### 3. Create `nginx.conf`

```nginx
server {
    listen 80;
    server_name _;
    root /var/www/mirror;
    charset utf-8;

    access_log /var/log/nginx/access.log;
    error_log  /var/log/nginx/error.log warn;

    client_max_body_size 0;
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;

    location / {
        autoindex on;
        autoindex_exact_size off;
        autoindex_localtime on;
        autoindex_format html;

        location ~* \.(db|tar|gz|xz|zst|iso|rpm|deb|pkg|sig)$ {
            expires 1d;
            add_header Cache-Control "public, immutable";
        }
    }
}
```

### 4. Create `rsyncd.conf`

#### Option A: Single module (simplest, dynamic)

One module serves the entire mirror tree. Any new distribution added to `/mnt/big/mirrors` is automatically available.

```ini
pid file = /var/run/rsyncd.pid
max connections = 20
timeout = 600

[mirrors]
path = /data
comment = All Linux Mirrors
read only = yes
list = yes
uid = root
gid = root
```

When using this option, clients must include the module name in the path:
```toml
baseUrl = "rsync://192.168.1.22/mirrors"
sourcePath = "fedora/linux/releases/44/Everything/x86_64/os/"
```

#### Option B: Per-distribution modules

Useful if you want strict module boundaries or different access rules per distro.

```ini
pid file = /var/run/rsyncd.pid
max connections = 20
timeout = 600

[archlinux]
path = /data/archlinux
comment = Arch Linux Mirror
read only = yes
list = yes
uid = root
gid = root

[debian]
path = /data/debian
comment = Debian Mirror
read only = yes
list = yes
uid = root
gid = root

[fedora]
path = /data/fedora
comment = Fedora Mirror
read only = yes
list = yes
uid = root
gid = root

[fedora-updates]
path = /data/fedora-updates
comment = Fedora Updates Mirror
read only = yes
list = yes
uid = root
gid = root
```

When using this option, clients connect directly to the module:
```toml
baseUrl = "rsync://192.168.1.22"
sourcePath = "fedora/Everything/x86_64/os/"
```

### 5. Start the server

```bash
docker compose up -d
```

### 6. Verify both services

**HTTP (nginx):**
```bash
curl -I http://192.168.1.22/
# or open http://192.168.1.22/ in browser
```

**Rsync:**
```bash
rsync rsync://192.168.1.22/
# Should list: mirrors (Option A) or archlinux, debian, fedora... (Option B)
```

## Integration with JazzySync

### On the master server (downloads from internet)

```toml
baseUrl = "rsync://mirror.yandex.ru/"
targetDir = "/mnt/big/mirrors"
logDir = "~/.cache/jazzy"

[distros.arch]
sourcePath = "archlinux/"
family = "arch"
enabled = true

[distros.debian]
sourcePath = "debian/"
family = "debian"
enabled = true

[distros.fedora]
sourcePath = "fedora/linux/releases/44/Everything/x86_64/os/"
family = "fedora"
enabled = true

[distros.fedora-updates]
sourcePath = "fedora/linux/updates/44/Everything/x86_64/"
family = "fedora"
enabled = true
```

Run:
```bash
jazzy sync
```

### On a slave server (downloads from your local mirror)

Copy the same `config.toml`, change only `baseUrl`:

```toml
# Option A (single [mirrors] module)
baseUrl = "rsync://192.168.1.22/mirrors"

# Option B (per-distribution modules)
# baseUrl = "rsync://192.168.1.22"
```

Everything else stays the same:
```toml
targetDir = "/mnt/data6/mirrors"
logDir = "~/.cache/jazzy"

[distros.arch]
sourcePath = "archlinux/"
family = "arch"
enabled = true

# ... etc
```

Run:
```bash
jazzy sync
```

## Firewall notes

Make sure these ports are open on the mirror server:
- `TCP 80` — HTTP (for `pacman`, `apt`, `dnf`)
- `TCP 873` — rsync (for `jazzy sync`)

Example with `ufw`:
```bash
sudo ufw allow 80/tcp
sudo ufw allow 873/tcp
```

## Troubleshooting

### `Unknown module 'xxx'`

The rsync daemon is running with an old config. Restart it:
```bash
cd ~/docker/linux-mirror
docker compose restart rsync
```

### `No such file or directory` (rsync)

The `sourcePath` on the client does not match the directory structure on the server. Check what actually exists:
```bash
rsync rsync://192.168.1.22/mirrors/
# or
rsync rsync://192.168.1.22/
```

Then adjust `sourcePath` in the client's `config.toml`.

### HTTP returns 404

Ensure the volume mount in `compose.yml` points to the correct host path and that the directories exist:
```bash
ls -la /mnt/big/mirrors/
```

---

## See Also

- [Client Setup](CLIENT_SETUP.md) — configure **pacman**, **apt**, and **dnf** to use your mirror
