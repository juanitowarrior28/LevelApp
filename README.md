# LevelApp - Dosimetría Acústica Ocupacional

LevelApp es una aplicación Android modular para la medición de ruido y cálculo de dosis acústica en entornos laborales. Combina una arquitectura limpia por capas con un motor DSP (Procesamiento Digital de Señales) en tiempo real diseñado para soportar jornadas completas de **8+ horas**, persistencia local en Room y exportación de reportes técnicos en **PDF** y **CSV**.

---

## 🚀 Características Principales

* **Medición Continua para Jornadas de 8+ Horas (`Foreground Service`):**
  * Captura en segundo plano con hilo de audio de alta prioridad (`THREAD_PRIORITY_URGENT_AUDIO`), `PARTIAL_WAKE_LOCK` y solicitud de exención de ahorro de batería.
  * Auto-recuperación del micrófono ante interrupciones del sistema (llamadas, cambios de foco de audio).
  * Checkpoint binario atómico completo (`filesDir/active_session_checkpoint.bin`) que guarda acumuladores, dosis, el histograma completo de percentiles y todos los intervalos temporales (*time slices*). Si Android cierra el proceso en segundo plano, al reanudar la sesión continúa exactamente donde quedó y registra la hora de reanudación en el reporte.
* **Motor DSP de Ponderación A y Slow (IEC 61672-1):**
  * Captura PCM de 16 bits Mono a **48 kHz** procesada en bloques de **100 ms** ($4\,800\text{ muestras}$).
  * Descarte de transitorios de arranque del hardware (*warm-up gate* de $500\text{ ms}$) con pre-carga del integrador Slow para suprimir picos y mínimos falsos al iniciar.
  * Filtro digital IIR de **6.º orden** en Forma Directa II Transpuesta (DF-II-T) + integrador exponencial **Slow** ($\tau = 1.0\text{ s}$) muestra a muestra sobre la energía cuadrática media ($L_{\text{ASmax}}, L_{\text{ASmin}}$).
  * **Integración sin recorte:** La energía, dosis, histograma ($20\text{–}140\text{ dB(A)}$) y percentiles se calculan con el nivel real completo (protegiendo $\log_{10}(0)$ con piso técnico $10^{-10}$). El rango confiable del hardware MEMS es **35–110 dB(A)** (recorte exclusivo para la visualización en pantalla y contabilización automática de tiempo bajo $35\text{ dBA}$ o sobre $110\text{ dBA}$).
* **Dosimetría Multi-Criterio en Paralelo (5 Perfiles):**
  La pantalla principal muestra un selector simplificado de 3 criterios (**NIOSH** | **OSHA** | **COLOMBIA**) que se pueden cambiar en tiempo real sin perder lo acumulado. Internamente se integran de forma incremental y simultánea los cinco perfiles, y los cinco aparecen con su Dosis y TWA en la tabla comparativa del PDF y del CSV:
  * **NIOSH REL:** Criterio $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
  * **OSHA PEL** *(29 CFR 1910.95 Table G-16)*: Criterio $90.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 90.0\text{ dB(A)}$.
  * **OSHA HC** *(29 CFR 1910.95 Hearing Conservation / Action Level)*: Criterio $90.0\text{ dB(A)}$ / $8\text{ h}$ ($50\%\text{ dosis} = 85\text{ dB(A)}$), tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
  * **ISO 9612:** Método internacional de determinación de $L_{\text{EX,8h}}$ con referencia $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, sin umbral de corte ($L_{\text{th}} = 0.0\text{ dB(A)}$).
  * **Res. 1792 (Col)** *(Resolución 1792 de 1990 - Colombia)*: Criterio legal colombiano de $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
* **Métricas Acústicas y Estadísticas Separadas:**
  * **$L_{\text{EX,8h}}$ (ISO 9612):** Nivel de exposición diario normalizado a $8\text{ h}$, calculado por energía sin umbral ($L_{\text{Aeq},T} + 10\log_{10}(T_{\text{horas}}/8)$) usando el tiempo realmente medido (excluye pausas), idéntico para todos los criterios.
  * **$\text{TWA}$ del criterio:** Calculado a partir de la dosis del criterio elegido ($L_c + k \log_{10}(D/100)$). Si $D < 0.01\text{ \%}$, se muestra `"Bajo umbral (< XX dBA)"` en UI, PDF y CSV.
  * **$L_{\text{Aeq},T}$ y $\text{SEL}$ ($L_{\text{AE}}$):** Nivel continuo equivalente ponderado A y Nivel de Exposición Sonora integrado a $1\text{ s}$.
  * **Exposición Sonora Física ($E_A$):** Energía en $\text{Pa}^2\cdot\text{h}$ ($1.01\text{ Pa}^2\cdot\text{h} \equiv 100\%$ a $85\text{ dB(A)}$), formateada en notación científica (ej. `2.68 × 10⁻⁴ Pa²·h`) cuando es menor a $0.01$.
  * **Extremos Slow y Percentiles:** $L_{\text{ASmax}}$, $L_{\text{ASmin}}$ y percentiles estadísticos **$L_{10}$**, **$L_{50}$**, **$L_{90}$** evaluados con centro de bin en $[20, 140]\text{ dB(A)}$.
  * **Indicadores en lenguaje claro para el usuario:** Frase de estado semáforo (*Exposición aceptable*, *Cerca del límite*, *Límite superado*), **tiempo restante hasta 100%** al nivel actual (`HH:mm`), alerta no bloqueante ante saturación ($> 110\text{ dBA}$) y recordatorio discreto de calibración.
* **Reportes Técnicos en PDF (Tema Blanco) y CSV (Serie Temporal Adaptativa):**
  * **Formulario Ocupacional al Guardar:** Nombre de la medición, trabajador, cargo / GES, área o lugar, evaluador / licencia SST y observaciones.
  * **Reporte PDF Formal:** Hoja en tema blanco con bloque ordenado de resultados, tabla comparativa de los 5 perfiles (Dosis y TWA), avisos de calidad de medición cuando aplican y recuadros de firma.
  * **Exportación CSV Estadística:** Resumen ordenado, tabla comparativa de los 5 perfiles, avisos de calidad y serie temporal paso a paso con promedio energético logarítmico exacto y ventana adaptativa ($1\text{ s}, 5\text{ s}, 15\text{ s}, 30\text{ s}, 60\text{ s}$).

---

## 🧩 Estructura de Módulos

* **`:core`**: Motor DSP puro en Kotlin (`AWeightingFilter`, `Calibration`, `SlowIntegrator`), perfiles normativos (`RegulatoryProfile`), calculador de métricas (`DoseCalculator`) y motor de sesión/checkpoint (`DosimetrySessionEngine`), cubiertos por pruebas unitarias JUnit 5.
* **`:data`**: Persistencia local con Room Database v5 (`SessionEntity`, `MeasurementPointEntity`, `AcousticSessionDao`, migraciones incrementales `1→2→3→4→5` y `SessionRepositoryImpl`).
* **`:app`**: Interfaz Jetpack Compose (`AcousticSessionScreen`, `HistoryScreen`), servicio de captura en primer plano (`AcousticForegroundService`), puerta de enlace (`AudioDataStream`) y exportadores de reportes (`PdfReportExporter`, `CsvReportExporter`, `ShareUtils`).

---

## 🛠️ Requisitos y Construcción

* Android Studio Iguana+, Java JDK 17+, Android API 26–34.

```bash
# Compilar el APK de depuración
./gradlew :app:assembleDebug

# Ejecutar las pruebas unitarias del núcleo DSP y dosimétrico
./gradlew :core:test
```

---

## ⚠️ Advertencia Metrológica y Legal

**LevelApp tiene fines preventivos, referenciales, de ingeniería asistencial y educativos.**  
Esta aplicación no sustituye a un dosímetro acústico integrador Clase 1 o Clase 2 certificado bajo IEC 61672 / ANSI S1.25. El **rango confiable** habitual de los micrófonos MEMS de smartphones es **35–110 dB(A)**; fuera de ese intervalo la app sigue integrando sin recortar pero emite avisos de piso de ruido o posible saturación. Para mediciones en campo se requiere configurar la **Calibración de Offset** contrastándola con un sonómetro o calibrador patrón.

---

## 👥 Autores y Reconocimientos

Proyecto desarrollado por:
* **Cielo Isabel Ochoa**
* **Juan José Guerrero**
* *"Mel Osako"*
