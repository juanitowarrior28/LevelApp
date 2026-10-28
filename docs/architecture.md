# Arquitectura

La solución sigue una arquitectura modular con separación explícita por capas:

- `core`: motor DSP, formulación de SPL, ponderación A, Slow, dosis y perfiles regulatorios.
- `data`: persistencia local con Room, entidades, DAO y repositorios.
- `app`: Android UI, permisos de micrófono, servicio foreground y gestión de sesiones.

Esta separación evita mezclar medición acústica con criterio de cumplimiento. El núcleo del sistema realiza cálculo de señales y energía acústica; los perfiles (`NIOSH`, `OSHA`, `ISO`) definen criterios regulatorios aplicables y no modifican el motor DSP.
