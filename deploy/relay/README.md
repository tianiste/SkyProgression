# Profile relay (fixed-IP server)

Hypixel application keys can be locked to an IP allowlist. A Cloudflare Worker has no fixed outbound IP, so the hosted
API forwards `/profile` to this relay: the companion running in relay mode on a small server with a reserved public IP,
behind Caddy for HTTPS. Prices, resale and names stay on the Worker.

```
mod -> https://api.skyprogression.dev (Worker) -> https://relay.skyprogression.dev (Caddy -> companion) -> Hypixel
```

In relay mode (`SKYPROGRESSION_RELAY_SECRET` set) the companion answers only `/health` and `/profile`, and `/profile` only
when the request carries the secret in `X-Relay-Secret`. Everything else answers 404. It fetches no prices.

Tested target: Oracle Cloud Always Free `VM.Standard.E2.1.Micro` (1/8 OCPU, 1 GB) with Ubuntu 24.04. Any small Linux VM
with a fixed IP works the same way.

## 1. Server, IP and DNS

1. Create the VM (Ubuntu, your SSH key). Reserve a public IP (Networking > Reserved public IPs) and attach it to the
   VM's network interface, so the IP survives rebuilds.
2. Open TCP 80 and 443: in the VM subnet's security list add ingress rules from `0.0.0.0/0`, and on the VM itself
   (Oracle's Ubuntu images also filter with iptables):
   ```bash
   # Insert above Oracle's REJECT rule (its line number differs between images).
   R=$(sudo iptables -L INPUT -n --line-numbers | awk '/REJECT/ {print $1; exit}')
   sudo iptables -I INPUT $R -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo iptables -I INPUT $R -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo netfilter-persistent save
   sudo iptables -L INPUT -n --line-numbers   # 80 and 443 must be listed above REJECT
   ```
   Port 18475 stays closed: the companion only listens on 127.0.0.1.
3. In Cloudflare DNS for `skyprogression.dev`, add an `A` record `relay` -> the reserved IP with **Proxy status: DNS
   only** (grey cloud), so Caddy can obtain its certificate and Hypixel sees the VM's IP.
4. On the Hypixel Developer Dashboard, put the reserved IP on the application key's allowlist.

## 2. Java 25 and Caddy

```bash
sudo apt-get update && sudo apt-get install -y wget gpg apt-transport-https debian-keyring debian-archive-keyring curl
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update && sudo apt-get install -y temurin-25-jre caddy
```

Sources: [Adoptium Linux packages](https://adoptium.net/installation/linux/), [Caddy install](https://caddyserver.com/docs/install).

## 3. Companion

On your PC, build it (`./gradlew :service:installDist`) and copy it with this folder:

```bash
scp -r service/build/install/service deploy/relay ubuntu@<reserved IP>:/tmp/
```

On the server:

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin skyprogression
sudo mkdir -p /opt/skyprogression /etc/skyprogression
sudo rm -rf /opt/skyprogression/service && sudo mv /tmp/service /opt/skyprogression/service
sudo install -m 640 -o root -g skyprogression /tmp/relay/relay.env.example /etc/skyprogression/relay.env
sudo nano /etc/skyprogression/relay.env     # paste the Hypixel application key and a secret from: openssl rand -hex 32
sudo install -m 644 /tmp/relay/skyprogression-relay.service /etc/systemd/system/
sudo install -m 644 /tmp/relay/Caddyfile /etc/caddy/Caddyfile
sudo systemctl daemon-reload && sudo systemctl enable --now skyprogression-relay && sudo systemctl reload caddy
curl -s https://relay.skyprogression.dev/health        # {"ok":true,"ready":true}
```

Logs: `journalctl -u skyprogression-relay -e` (they never contain the key or the secret).
Update: copy a new build to `/tmp/service`, repeat the `rm`/`mv` line and `sudo systemctl restart skyprogression-relay`.

## 4. Worker

In the SkyProgression-API checkout:

```bash
npx wrangler secret put RELAY_SECRET            # the same secret as on the server
npx wrangler secret delete HYPIXEL_API_KEY      # optional: the relay holds the key now
```

Set `"RELAY_URL": "https://relay.skyprogression.dev"` under `vars` in `wrangler.jsonc` and run `npx wrangler deploy`.
`/health` then reports `"relay": true`, and `/profile` answers come from the relay.
