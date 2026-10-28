# DSP y ponderación frecuencial

El motor DSP se enfoca en la conversión de una señal digital en energía y niveles equivalentes. Se calcula RMS y se utiliza la relación física entre presión y energía para derivar un nivel en dB relativo al punto de referencia utilizado en calibración.

La ponderación A se implementa como un conjunto de etapas de segundo orden y la ponderación temporal Slow se materializa mediante una integración de energía con constante de tiempo de 1 s. La lógica de este motor queda separada de los criterios regulatorios para facilitar la extensión a perfiles adicionales.
