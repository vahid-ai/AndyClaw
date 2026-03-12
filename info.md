ethOS is a custom Android-based operating system built for the dGEN1 device — a crypto-focused smartphone.

Key characteristics from the codebase:

- Crypto-native: Has a built-in system wallet with on-device transaction approval
- Extended Android APIs: Provides proprietary SDKs not available on stock Android:
    - DgenSubAccountSDK — agent-controlled sub-accounts
    - MessengerSDK — messaging integration
    - ContactsSDK — contacts system
    - TerminalSDK — terminal/shell access (Termux-like)
- Privileged app support: Apps can be installed as system apps with elevated permissions (SYSTEM_APP=true)
- Heartbeat integration: ethOS can directly trigger AndyClaw's background agent loop, whereas stock Android requires a foreground service

In this project, skills and features are tiered:
- TIER_ALL — works on any Android
- TIER_ETHOSONLY — requires ethOS-specific APIs
- TIER_PRIVILEGED — requires system-level permissions only available on ethOS

Essentially, ethOS is a privacy/crypto-focused Android fork designed for the dGEN1 hardware, giving apps like AndyClaw deeper system
access than standard Android would allow