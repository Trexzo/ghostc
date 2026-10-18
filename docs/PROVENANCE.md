# Architecture references and provenance policy

Five user-supplied Library archives were inventoried read-only before repository creation: recovered Vape V4.21, OpenSlinky, a Slinky source-build workspace, OpenOnyx, and an OpenB4 Forge source bundle. The source archives are NOT committed. The names identify references, not dependencies or proof of legitimacy.

| Reference | Design research only | Caution |
| --- | --- | --- |
| Vape V4.21 recovery | GUI, profiles, client convenience | recovered binaries/source may be proprietary; unverified behavioral parity |
| OpenSlinky / Slinky build | module and service separation | injection and native loader paths are not adopted |
| OpenOnyx | render, HUD and layout organization | no independently demonstrated full source build |
| OpenB4 | Forge adapter integration ideas | don't copy static global module state or authentication flows |

All checked-in Java code is original GhostFusion R0.1 core code. Future submissions must identify the original author, upstream URL/commit, applicable license and modifications. Do not accept code merely because a ZIP is labeled "open", "clean" or "decompiled". Never commit third-party cracked binaries, paid-client sources, private tokens, user profiles or downloaded native executables.
