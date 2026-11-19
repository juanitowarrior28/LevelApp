# LevelApp - Dosimetría Acústica Ocupacional

LevelApp es una aplicación Android modular para la medición de ruido y cálculo de dosis acústica en entornos laborales. Combina una arquitectura limpia por capas con un motor DSP (Procesamiento Digital de Señales) en tiempo real diseñado para soportar jornadas completas de **8+ horas**, persistencia local en Room y exportación de reportes técnicos en **PDF** y **CSV**.

---

## 🚀 Características Principales

* **Medición Continua para Jornadas de 8+ Horas (`Foreground Service`):**
  * Captura en segundo plano con hilo de audio de alta prioridad (`THREAD_PRIORITY_URGENT_AUDIO`), `PARTIAL_WAKE_LOCK` y solicitud de exención de ahorro de batería.
  * Auto-recuperación del micrófono ante interrupciones del sistema (llamadas, cambios de foco de audio).
  * Respaldo automático de estado en disco (*checkpointing* cada 5 s en `SharedPreferences`) y estructuras de memoria acotadas en $O(1)$ para evitar pérdidas de datos o desbordamientos de memoria.
* **Motor DSP de Ponderación A (IEC 61672-1):**
  * Captura PCM de 16 bits Mono a **48 kHz** procesada en bloques de **100 ms** ($4\,800\text{ muestras}$).
  * Descarte de transitorios de arranque del hardware (*warm-up gate* de $500\text{ ms}$) para eliminar picos falsos al iniciar la captura.
  * Filtro digital IIR de **6.º orden** en Forma Directa II Transpuesta (DF-II-T) con rango dinámico operativo unificado de **35.0 a 110.0 dB(A)**.
* **Dosimetría Multi-Criterio en Paralelo (5 Perfiles):**
  La pantalla principal muestra un selector simplificado de 3 criterios (**NIOSH** | **OSHA** | **COLOMBIA**) que se pueden cambiar en tiempo real sin perder lo acumulado. Internamente se integran de forma incremental y simultánea los cinco perfiles, y los cinco aparecen en los reportes PDF y CSV:
  * **NIOSH REL:** Criterio $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
  * **OSHA PEL** *(29 CFR 1910.95 Table G-16)*: Criterio $90.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 90.0\text{ dB(A)}$.
  * **OSHA HC** *(29 CFR 1910.95 Hearing Conservation / Action Level)*: Criterio $90.0\text{ dB(A)}$ / $8\text{ h}$ ($50\%\text{ dosis} = 85\text{ dB(A)}$), tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
  * **ISO 9612:** Método internacional de determinación de $L_{\text{EX,8h}}$ con referencia $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 3.0\text{ dB}$, sin umbral de corte ($L_{\text{th}} = 0.0\text{ dB(A)}$).
  * **Res. 1792 (Col)** *(Resolución 1792 de 1990 - Colombia)*: Criterio legal colombiano de $85.0\text{ dB(A)}$ / $8\text{ h}$, tasa de intercambio $q = 5.0\text{ dB}$, umbral $L_{\text{th}} = 80.0\text{ dB(A)}$.
* **Métricas Acústicas y Estadísticas Completas:**
  * **$L_{\text{Aeq},T}$:** Nivel continuo equivalente ponderado A.
  * **$\text{SEL}$ ($L_{\text{AE}}$):** Nivel de Exposición Sonora ponderado A integrado a $1\text{ s}$.
  * **$L_{\text{EX,8h}}\text{ / TWA}$:** Nivel de exposición diario normalizado a 8 horas.
  * **Exposición Sonora Física ($E_A$):** Energía en $\text{Pa}^2\cdot\text{h}$ según ISO 9612 ($1.01\text{ Pa}^2\cdot\text{h} \equiv 100\%$ a $85\text{ dB(A)}$).
  * **Extremos y Percentiles:** $L_{\text{ASmax}}$, $L_{\text{ASmin}}$ y percentiles estadísticos **$L_{10}$** (picos frecuentes), **$L_{50}$** (mediana acústica) y **$L_{90}$** (ruido de fondo).
  * **Indicadores de Dosis:** Dosis acumulada (%), porcentaje restante de exposición permitida (%) y dosis proyectada a 8 horas (%).
* **Reportes Técnicos en PDF (Tema Blanco) y CSV (Serie Temporal Adaptativa):**
  * **Formulario Ocupacional al Guardar:** Captura de nombre de la medición, nombre completo del trabajador, cargo / GES, área o lugar de medición, evaluador / licencia SST y observaciones.
  * **Reporte PDF Formal:** Hoja en tema blanco optimizada para impresión y firma, con tipografía *Bricolage Grotesque*, barra de dosis, tabla oficial de registro ocupacional, comparativa en los 5 criterios y recuadros de firma del trabajador y del evaluador SST.
  * **Exportación CSV Estadística:** Incluye resumen ejecutivo completo y serie temporal paso a paso hacia abajo con **promedio energético logarítmico exacto** y ventana adaptativa según la duración ($1\text{ s}$ hasta $3\text{ min}$, $5\text{ s}$ de $3\text{ a } 15\text{ min}$, $15\text{ s}$ de $15\text{ a } 60\text{ min}$, $30\text{ s}$ de $1\text{ a } 4\text{ h}$ y $60\text{ s}$ para $> 4\text{ h}$).
* **Alertas Hápticas, Calibración y Onboarding:**
  * Alarma por vibración al alcanzar el 100% de la dosis diaria.
  * Ajuste de **Offset de Calibración (dB)** frente a sonómetro o calibrador patrón.
  * Recorrido interactivo (*Spotlight Guided Tour*) y guía integrada de interpretación de resultados.

---

## 🧩 Estructura de Módulos

El proyecto está dividido en tres módulos siguiendo principios de separación de responsabilidades:

* **`:core`**: Motor matemático DSP puro en Kotlin (`AWeightingFilter`, `Calibration`, `SlowIntegrator`), perfiles normativos (`RegulatoryProfile`) y calculador de dosis/TWA (`DoseCalculator`), cubiertos por pruebas unitarias JUnit 5.
* **`:data`**: Persistencia local con Room Database v4 (`SessionEntity`, `MeasurementPointEntity`, `AcousticSessionDao`, migraciones incrementales `1→2→3→4` y `SessionRepositoryImpl`).
* **`:app`**: Interfaz en Jetpack Compose (`AcousticSessionScreen`, `HistoryScreen`), servicio de captura en primer plano (`AcousticForegroundService`), motor de sesión en tiempo real (`AudioDataStream`) y exportadores de reportes (`PdfReportExporter`, `CsvReportExporter`, `ShareUtils`).

---

## 🛠️ Requisitos del Entorno

* Android Studio Iguana (o superior).
* Java JDK 17+.
* Dispositivo Android con API mínima 26 (Android 8.0) y Target API 34.

---

## 💻 Construcción y Ejecución

```bash
# Compilar el APK de depuración
./gradlew :app:assembleDebug

# Ejecutar las pruebas unitarias del núcleo DSP y dosimétrico
./gradlew :core:test
```

---

## ⚠️ Advertencia Metrológica y Legal

**LevelApp tiene fines preventivos, referenciales, de ingeniería asistencial y educativos.**  
Esta aplicación no sustituye a un dosímetro acústico integrador Clase 1 o Clase 2 certificado bajo IEC 61672 / ANSI S1.25. Los micrófonos MEMS de los smartphones presentan un piso de ruido propio ($\sim 30\text{–}35\text{ dB(A)}$) y limitación física por sobrecarga acústica cerca de $110\text{ dB(A)}$. Para mediciones en campo se requiere configurar la **Calibración de Offset** contrastándola con un instrumento patrón. Los resultados generados no tienen validez pericial o sancionatoria sin trazabilidad metrológica.

---

## 👥 Autores y Reconocimientos

Proyecto desarrollado por:
* **Cielo Isabel Ochoa**
* **Juan José Guerrero**
* *"Mel Osako"*
