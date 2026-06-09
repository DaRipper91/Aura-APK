# 🌟 AURA PROJECT MANDATES

Any agent operating within the Aura-APK or Project Aura repositories must adhere to the following core mandates to ensure system integrity, performance, and aesthetic consistency.

## ⚡ BOLT (PERFORMANCE)
- **Engine Optimization:** Favor `__slots__` in Python classes to reduce memory footprint.
- **Latency First:** Eliminate UI micro-stutters by caching hardware detection and offloading heavy inference to the Logic Hub.
- **Asahi Priority:** Detect and optimize for Apple Silicon (Metal/GPU) when running on local Asahi Linux environments.

## 🛡️ SENTINEL (SECURITY)
- **Command Injection Prevention:** Use list-based `argv` for all subprocess/shell calls. Never use `shell=True` with user-supplied input.
- **Biometric Enforcement:** Critical Android operations (Vault/Settings) must be gated by the `BiometricHelper`.
- **Integrity Checks:** The engine must perform backend health checks (`scan_integrity`) before initiating inference.

## 🎨 PALETTE (AESTHETICS)
- **Identity:** Use the **Obsidian/Gold/Purple** theme (Background: `0xFF0F0F0F`, Accents: `0xFFD4AF37`, `0xFF8833FF`).
- **Focus:** Ensure explicit `:focus` and `:hover` states for keyboard-driven and controller-driven navigation.
- **Signal-to-Noise:** Minimize conversational filler. The UI and the Agent should provide high-signal technical data only.

---

# 🛸 LOGIC HUB (DA-HP) ARCHITECTURE

The Aura-APK operates as a **Satellite Interface** to the **DA-HP Logic Hub**.

1. **SHUT UP AND COMPUTE:** Agents must provide direct, technical responses without conversational padding.
2. **SATELLITE VIEW:** Always analyze the entire workspace (`python/`, `android/`) before proposing or implementing changes.
3. **REMOTE-FIRST:** The APK is configured to offload inference to `http://<TAILSCALE-IP>:11434`. Do not re-enable local model downloads unless explicitly instructed.
4. **NON-INTERACTIVE:** Use non-interactive flags (`--yes`, `--no-pager`) for all internal CLI integrations.
