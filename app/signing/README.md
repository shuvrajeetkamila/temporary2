# Public preproduction Android signing key

`qatar-preproduction.p12` is intentionally committed and its password is intentionally public. It is **not** a production credential and grants no production trust.

Purpose: GitHub-hosted clean runners otherwise create a different Android debug certificate for every build. Android then rejects an APK update over an earlier build of the same package with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Starting at version code 5, preproduction APKs use this fixed development certificate so later preproduction builds can update one another.

- Alias: `qatar-preproduction`
- Store/key password: `qatar-preproduction-public`
- Certificate SHA-256: `CC:CE:47:E3:41:DD:E3:7E:9C:B9:4A:B7:CB:92:98:EE:39:DF:96:74:83:CA:6C:61:84:91:CD:43:A7:AE:39:0C`
- Certificate validity: 2026-10-15 through 2036-10-12
- Keystore SHA-256: `2edd3c03193dee5cac2c393190d74ae6823c4f165e31eb763eab13991f7a6746`

Users must uninstall v0.4 and older once because those APKs used ephemeral CI debug certificates. v0.5 and later preproduction builds can then be installed as updates while this package ID and certificate remain unchanged.

A production release must use a private key held outside the repository and must never reuse this public key.
