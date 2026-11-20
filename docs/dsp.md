# DSP y ponderación frecuencial

El motor DSP procesa audio PCM de 16 bits Mono a $f_s = 48\,000\text{ Hz}$ en bloques discretos de $\Delta t = 100\text{ ms}$ ($4\,800\text{ muestras}$), descartando los primeros $500\text{ ms}$ (*warm-up gate*) para eliminar el transitorio de encendido del hardware.

1. **Ponderación frecuencial A (IEC 61672-1)**: filtro IIR digital de 6.º orden en Forma Directa II Transpuesta obtenido por transformación bilineal para $48\text{ kHz}$, normalizado a $0.0\text{ dB}$ en $1\text{ kHz}$.
2. **Ponderación temporal Slow ($\tau = 1.0\text{ s}$)**: `SlowIntegrator` integra muestra a muestra la energía cuadrática media con $\alpha = 1 - e^{-1/(f_s \cdot 1.0)}$ (pre-cargado con los últimos $300\text{ ms}$ del *warm-up*) para obtener $L_{\text{ASmax}}$ y $L_{\text{ASmin}}$ reales.
3. **Integración sin recorte**: El nivel calibrado $L_k$ se integra sin aplicar recorte en **35–110 dB(A)** (solo se protege $\log_{10}(0)$ con piso técnico $10^{-10}$), reservando el recorte `35–110 dB(A)` exclusivamente para la representación en pantalla y registrando el tiempo fuera del rango confiable.
4. **Histograma y pirámide temporal**: El histograma cubre $[20.0, 140.0]\text{ dB(A)}$ en pasos de $0.1\text{ dB}$ (1201 bins) calculando $L_{10}, L_{50}, L_{90}$ con centro de bin, y la pirámide temporal conserva la energía exacta para exportación CSV, respaldada íntegramente en un checkpoint binario atómico.
