# LevelApp

Aplicación Android modular para dosimetría acústica ocupacional con arquitectura limpia, motor DSP dedicado, calibración explícita, perfiles regulatorios desacoplados y persistencia local segura.

## Fases implementadas

1. Arquitectura modular y separación de capas.
2. Captura de audio en tiempo real con permisos y servicio foreground.
3. Motor DSP base: RMS, SPL digital, ponderación A y integrador Slow.
4. Calibración y estado de validación metrológica.
5. Dosimetría NIOSH con dosis y TWA separados del núcleo acústico.
6. Persistencia local con Room para historial y puntos agregados.
7. UI moderna con Jetpack Compose y estados en tiempo real.
8. Exportación local PDF y preparación para Share Sheet.

## Módulos

- `:core`: DSP, calibración, filtros, perfiles regulatorios y pruebas unitarias.
- `:data`: entidades Room, DAO y repositorio local.
- `:app`: UI, permisos, servicio foreground, exportación PDF y entrada principal.

## Requisitos

- Android Studio Iguana o posterior.
- JDK 17.
- Android SDK con API 34 instalada.

## Ejecución

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :core:test
./gradlew :app:compileDebugKotlin
```

## Limitaciones regulatorias y metrológicas

La aplicación no se presenta como instrumento certificado ni como cumplimiento formal con IEC, ISO, NIOSH u otra normativa. La medición se trabaja con estados de calibración claramente diferenciados:

- `NOT_CALIBRATED`
- `CALIBRATED_UNVALIDATED`
- `VALIDATED`

La validación formal requiere trazabilidad metrológica y procedimientos de calibración documentados.
