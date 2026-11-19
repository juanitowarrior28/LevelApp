# Dosimetría ocupacional

El motor de dosimetría integra la exposición en bloques discretos de $\Delta t = 100\text{ ms}$ dentro de un rango operativo unificado de **35.0 a 110.0 dB(A)** (acorde con la referencia de fondo de escala digital $0\text{ dBFS} \equiv 100\text{ dB SPL}$, el piso de ruido propio de micrófonos MEMS y su punto de sobrecarga acústica).

Se implementan cinco perfiles en paralelo (`RegulatoryProfile`):

- **NIOSH REL**: criterio $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
- **OSHA PEL** (*29 CFR 1910.95 Table G-16*): criterio $90.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 90.0\text{ dB(A)}$.
- **OSHA HC** (*29 CFR 1910.95 Hearing Conservation / Action Level*): criterio $90.0\text{ dB(A)}$ / $8\text{ h}$ ($50\%\text{ dosis} = 85\text{ dB(A)}$), tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
- **ISO 9612**: método internacional de determinación de $L_{\text{EX,8h}}$ con referencia $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, sin umbral ($L_{\text{th}} = 0.0\text{ dB(A)}$).
- **Res. 1792 (Col)** (*Resolución 1792 de 1990 - Colombia*): criterio $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.

La fórmula incremental aplicada en cada intervalo $\Delta t_k$ (en horas) es:

- $\Delta\text{Dosis}\% = 100 \cdot (\Delta t_k / T_{\text{ref}}) \cdot 2^{(L_k - L_c) / q}$ si $L_k \ge L_{\text{th}}$, o $0\%$ si $L_k < L_{\text{th}}$.

El Nivel de Exposición Sonora ponderado A se reporta exclusivamente como **$\text{SEL}$ ($L_{\text{AE}}$)**:

- $\text{SEL} = L_{\text{AE}} = L_{\text{Aeq},T} + 10 \log_{10}(T_{\text{seg}} / 1\text{ s})$.
