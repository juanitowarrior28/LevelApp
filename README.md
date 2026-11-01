# LevelApp - Dosimetría Acústica Ocupacional

LevelApp es una aplicación Android modular y profesional para la medición de ruido y cálculo de dosis acústica en entornos laborales. Combina una arquitectura limpia con un motor DSP (Procesamiento Digital de Señales) avanzado para ofrecer métricas precisas en tiempo real, persistencia local y reportes detallados.

## 🚀 Características Principales

* **Medición en Segundo Plano (Foreground Service):** Captura continua de audio, permitiendo apagar la pantalla o cambiar de app sin detener la medición de ruido.
* **Procesamiento de Señal Avanzado (DSP):** Implementa un filtro digital IIR de Ponderación A (A-Weighting) adaptado a la respuesta del oído humano.
* **Dosimetría de Ruido:** Soporte dinámico para normativas internacionales **NIOSH** (límite 85dB / intercambio 3dB) y **OSHA** (límite 90dB / intercambio 5dB).
* **Alertas Hápticas:** Alarma por vibración automática cuando el trabajador alcanza el 100% de la dosis diaria segura.
* **Interfaz Moderna Oscura (Dark Theme):** Desarrollada 100% en Jetpack Compose, con gráficos (Canvas) de nivel sonoro en tiempo real y semaforización de riesgo.
* **Onboarding Interactivo (Spotlight Tour):** Tutorial guiado nativo (creado con Canvas y BlendMode.Clear) para enseñar el uso de la app paso a paso en el primer inicio.
* **Gestión de Sesiones (Room DB):** Historial persistente local para revisar, renombrar o eliminar sesiones pasadas de forma segura y privada.
* **Exportación de Reportes:** Generación de informes formales en **PDF** y exportación de datos crudos en **CSV**.
* **Calibración Manual:** Ajuste dinámico de Offset en dB para emparejar las lecturas del celular con un sonómetro físico patrón.

## 🧩 Estructura de Módulos

El proyecto está dividido en tres módulos siguiendo buenas prácticas de separación de responsabilidades:
- `:core`: Contiene las reglas de negocio puras, motor matemático DSP, filtros IIR, y cálculos de dosis/TWA.
- `:data`: Persistencia de datos utilizando Room Database (Entidades, DAO y Repositorio Local).
- `:app`: Capa de presentación (Jetpack Compose UI), Servicio en Segundo Plano (Micrófono), permisos y exportadores de archivos (PDF/CSV).

## 🛠️ Requisitos del Entorno

- Android Studio Iguana (o superior).
- Java JDK 17.
- Dispositivo Android con API mínima 26 (Android 8.0) y Target API 34.

## 💻 Construcción y Ejecución

Puedes compilar la aplicación utilizando Android Studio o directamente desde tu terminal con Gradle:

```bash
# Compilar la aplicación en modo Debug
./gradlew :app:assembleDebug

# Ejecutar las pruebas unitarias del núcleo matemático
./gradlew :core:test
```

## ⚠️ Advertencia Metrológica y Legal

**LevelApp tiene fines preventivos, referenciales y educativos.** 
Esta aplicación no sustituye a un dosímetro acústico integrador Clase 1 o Clase 2 certificado bajo las normativas internacionales (IEC 61672). Los micrófonos de los smartphones pueden presentar compresión acústica de hardware a altos niveles de presión. Para mediciones en campo, se requiere estrictamente configurar la **Calibración de Offset** comparándola con un instrumento patrón. Los resultados arrojados por esta app no tienen validez legal sancionatoria o pericial sin trazabilidad metrológica.
