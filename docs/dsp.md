# DSP y ponderación frecuencial

El motor DSP procesa audio PCM de 16 bits Mono a $f_s = 48\,000\text{ Hz}$ en bloques discretos de $\Delta t = 100\text{ ms}$ ($4\,800\text{ muestras}$), descartando los primeros $500\text{ ms}$ (*warm-up gate*) para eliminar el transitorio de encendido del hardware.

La ponderación frecuencial A (IEC 61672-1) se implementa como un filtro IIR digital de 6.º orden en Forma Directa II Transpuesta obtenido por transformación bilineal para $48\text{ kHz}$, normalizado a $0.0\text{ dB}$ en $1\text{ kHz}$.

A partir del valor eficaz normalizado ($\text{RMS}_{\text{norm}}$) de cada bloque y del offset de calibración, el nivel instantáneo $L_k$ se acota al rango operativo justificable del sistema (**35.0 a 110.0 dB(A)**) y alimenta:

- La integral de energía acústica ponderada A para derivar $L_{\text{Aeq},T}$, $\text{SEL}$ ($L_{\text{AE}}$) y la exposición $E_A$ ($\text{Pa}^2\cdot\text{h}$).
- El histograma temporal de $0.1\text{ dB}$ en memoria constante $O(1)$ para calcular los percentiles $L_{10}$, $L_{50}$ y $L_{90}$.
- La pirámide temporal auto-compactable que conserva exactamente la energía acústica de cada intervalo para la exportación CSV.
