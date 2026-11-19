# Arquitectura

La solución sigue una arquitectura modular con separación explícita por capas:

- `core`: motor DSP, formulación de SPL, filtro digital IIR de ponderación A (6.º orden IEC 61672 a 48 kHz), integrador Slow, cálculo incremental de dosis/TWA y perfiles regulatorios.
- `data`: persistencia local con Room Database (v4), entidades de sesión (`SessionEntity`) y de serie temporal (`MeasurementPointEntity`), DAO, migraciones y repositorio.
- `app`: interfaz Jetpack Compose, gestión de permisos y batería, servicio en primer plano (`AcousticForegroundService`), motor de integración en tiempo real (`AudioDataStream`) y exportadores técnicos en PDF y CSV.

Esta separación evita mezclar la medición física de energía acústica con los criterios regulatorios. El núcleo del sistema integra la energía ponderada A ($L_{\text{Aeq},T}$, $\text{SEL} / L_{\text{AE}}$, $E_A$) dentro del rango operativo de **35.0 a 110.0 dB(A)**, mientras que los perfiles (`NIOSH REL`, `OSHA PEL`, `OSHA HC`, `ISO 9612`, `Res. 1792 Colombia`) aplican sus respectivos niveles criterio, tasas de intercambio y umbrales en paralelo.
