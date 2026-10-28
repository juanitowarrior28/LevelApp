# Dosimetría ocupacional

Se implementa el perfil `NIOSH REL` con criterio de 85 dBA durante 8 horas, exchange rate de 3 dB y threshold de 80 dBA. En este enfoque, la dosis acumulada se calcula sobre la energía sonora efectiva dentro del periodo medido y se expresa como porcentaje respecto del criterio de referencia.

La fórmula aplicada para un nivel continuo de exposición es:

- Dosis% = 100 * (T / Tref) * 2^((L - C) / exchangeRate)

Donde:

- `T`: duración medida
- `Tref`: duración de referencia del perfil (8 h)
- `L`: nivel de exposición medido en dBA
- `C`: criterio del perfil
- `exchangeRate`: tasa de intercambio del perfil

El valor de TWA se deriva a partir del porcentaje de dosis acumulada y se mantiene separado del calculador de energía de la señal.
