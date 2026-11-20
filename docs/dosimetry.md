# Dosimetría ocupacional

El motor de dosimetría (`DosimetrySessionEngine` y `DoseCalculator`) integra la exposición en bloques discretos de $\Delta t = 100\text{ ms}$ con el **nivel real sin recortar** (el rango confiable **35–110 dB(A)** se usa únicamente para visualización y para contabilizar avisos de piso de ruido o posible saturación).

Se implementan cinco perfiles en paralelo (`RegulatoryProfile`), mostrando en la pantalla principal un selector simplificado de tres criterios (**NIOSH**, **OSHA**, **COLOMBIA**) mientras los cinco se integran simultáneamente para la tabla comparativa de los reportes PDF y CSV:

- **NIOSH REL**: criterio $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
- **OSHA PEL** (*29 CFR 1910.95 Table G-16*): criterio $90.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 90.0\text{ dB(A)}$.
- **OSHA HC** (*29 CFR 1910.95 Hearing Conservation / Action Level*): criterio $90.0\text{ dB(A)}$ / $8\text{ h}$ ($50\%\text{ dosis} = 85\text{ dB(A)}$), tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
- **ISO 9612**: método internacional de determinación de $L_{\text{EX,8h}}$ con referencia $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, sin umbral ($L_{\text{th}} = 0.0\text{ dB(A)}$).
- **Res. 1792 (Col)** (*Resolución 1792 de 1990 - Colombia*): criterio $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.

Métricas separadas usando exclusivamente el tiempo realmente medido $T_{\text{medido}}$ (excluyendo pausas):

- **$\Delta\text{Dosis}\%$** (incremental monotónica): $100 \cdot (\Delta t_k / T_{\text{ref}}) \cdot 2^{(L_k - L_c) / q}$ si $L_k \ge L_{\text{th}}$, o $0\%$ si $L_k < L_{\text{th}}$.
- **$L_{\text{EX,8h}}$ (ISO 9612, por energía sin umbral)**: $L_{\text{Aeq},T} + 10\log_{10}(T_{\text{horas}} / 8)$, idéntico para todos los criterios.
- **$\text{TWA}$ del criterio elegido**: $L_c + k\log_{10}(D / 100)$. Si $D < 0.01\text{ \%}$, se reporta `"Bajo umbral (< XX dBA)"` en UI, PDF y CSV.
- **$\text{SEL}$ ($L_{\text{AE}}$)**: $L_{\text{Aeq},T} + 10 \log_{10}(T_{\text{seg}} / 1\text{ s})$.
- **$E_A$ ($\text{Pa}^2\cdot\text{h}$)**: $4\times 10^{-10} \cdot T_{\text{horas}} \cdot 10^{L_{\text{Aeq},T}/10}$ (notación científica cuando es $< 0.01$).
