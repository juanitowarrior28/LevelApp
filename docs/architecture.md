# Arquitectura

La solución sigue una arquitectura modular con separación explícita por capas:

- `core`: motor DSP, formulación de SPL, filtro digital IIR de ponderación A (6.º orden IEC 61672-1 a 48 kHz), integrador Slow ($\tau = 1\text{ s}$), cálculo de dosis/$L_{\text{EX,8h}}$/$\text{TWA}$, perfiles regulatorios (`RegulatoryProfile`) y motor de sesión con checkpoint binario (`DosimetrySessionEngine`).
- `data`: persistencia local con Room Database (v5), entidades de sesión (`SessionEntity`) y de serie temporal (`MeasurementPointEntity`), DAO, migraciones (`1→2→3→4→5`) y repositorio.
- `app`: interfaz Jetpack Compose, gestión de permisos y batería, servicio en primer plano (`AcousticForegroundService`), puerta de enlace (`AudioDataStream`) y exportadores técnicos en PDF y CSV.

Esta separación evita mezclar la medición física de energía acústica con los criterios regulatorios. El núcleo integra la energía ponderada A ($L_{\text{Aeq},T}$, $L_{\text{EX,8h}}$, $\text{SEL}/L_{\text{AE}}$, $E_A$) **sin recorte previo** (el rango confiable **35–110 dB(A)** aplica solo para visualización y avisos de piso/saturación), mientras los perfiles (`NIOSH REL`, `OSHA PEL`, `OSHA HC`, `ISO 9612`, `Res. 1792 Col`) aplican sus criterios, tasas de intercambio y umbrales en paralelo.
