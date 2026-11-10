<div align="center">

<img src="docs/logo.svg" width="96" alt="Aether">

# Aether

A minimal Android VPN client built on [Xray-core](https://github.com/XTLS/Xray-core).

[![Release](https://img.shields.io/github/v/release/techalodev/aether?style=flat-square&color=4A35C9&label=release)](https://github.com/techalodev/aether/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/techalodev/aether/total?style=flat-square&color=4A35C9)](https://github.com/techalodev/aether/releases)
![Android](https://img.shields.io/badge/Android-8.0%2B-4A35C9?style=flat-square)
[![License](https://img.shields.io/badge/license-WTFPL-4A35C9?style=flat-square)](LICENSE)

[Download](https://github.com/techalodev/aether/releases/latest) · [Features](#features) · [Build](#build)

</div>

---

Aether runs Xray-core with a native TUN interface and wraps it in a quiet Material 3 Expressive interface: one button to connect, a list of servers, and the settings out of the way.

## Download

Get the APK from the [latest release](https://github.com/techalodev/aether/releases/latest).

| APK | Devices |
| --- | --- |
| `arm64-v8a` | Most phones from the last several years |
| `armeabi-v7a` | Older 32-bit devices |
| `universal` | Any ARM device, larger download |

The app checks GitHub Releases for updates and installs them in place.

## Features

### Protocols

VLESS (REALITY, XTLS Vision, XHTTP), VMess, Trojan, Shadowsocks and Hysteria2, over TCP, WebSocket, HTTPUpgrade, XHTTP, gRPC, HTTP/2 and mKCP transports.

### Subscriptions

- Base64 and plain link lists, Xray JSON configs and Clash / Mihomo YAML
- Traffic, expiry, title, announcements and support links from provider headers
- HWID headers compatible with Remnawave, switchable per subscription
- User-Agent presets for current Happ, v2rayNG and FlClash releases, or a custom one
- Per-subscription auto-update interval, name, User-Agent and HWID overrides
- Update report listing added, removed and changed servers

### Connection

- Real latency test: an HTTP GET through a temporary core, for all servers in parallel or one at a time
- Exit country detection, shown as flag, code or name
- Per-app split tunneling
- Quick Settings tile and import from the share sheet

### Routing

- Custom rules with Proxy, Direct and Block actions for domains, IPs, `geosite:` and `geoip:` entries
- Optional "Russia direct" preset: `.ru`, `.su`, `.рф`, `geosite:category-ru` and `geoip:ru` from [runetfreedom](https://github.com/runetfreedom/russia-v2ray-rules-dat), downloaded on demand
- Configurable sources for geo files

### Interface

- Material 3 Expressive with dynamic color, light and dark themes
- Server editor with field and raw modes, link sharing
- Collapsible, reorderable and renamable subscription groups
- Alternative app icons and an optional floating navigation bar
- Predictive back gesture

## Build

Requires JDK 21 and the Android SDK. The Xray core ([AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)) is downloaded and verified by SHA-256 on the first build.

```sh
./gradlew assembleRelease
```

Release signing reads `keystore.properties` locally or `AETHER_*` environment variables in CI. Without them the release build is signed with the debug key.

## Credits

- [Xray-core](https://github.com/XTLS/Xray-core) by XTLS
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) by 2dust
- [russia-v2ray-rules-dat](https://github.com/runetfreedom/russia-v2ray-rules-dat) by runetfreedom
- Google Sans Flex, licensed under the SIL Open Font License 1.1 (see `licenses/`)

## License

Aether's own code is released under the [WTFPL](LICENSE). It comes without any warranty, to the extent permitted by applicable law. Bundled and downloaded third-party components keep their own licenses.
