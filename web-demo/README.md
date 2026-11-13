# LevelApp Web Demo

Demo web para probar la UX y la lógica de cálculo de la app de dosimetría acústica.

## Cómo probarla

Puedes probarla en iPhone o Android desde:

**https://juanitowarrior28.github.io/LevelApp/**

Ábrela en Safari, pulsa **Iniciar** y acepta el permiso del micrófono. La demo necesita HTTPS para acceder al micrófono desde el navegador.

Para probarla localmente en una computadora:

```bash
cd web-demo
python3 -m http.server 8000
```

Luego abre `http://localhost:8000` en esa misma computadora.

## Importante

Este es un prototipo de prueba para UX y lógica. Mantén la página abierta durante la medición; el navegador puede detener la captura al bloquear el teléfono o cambiar de app. No sustituye mediciones metrológicamente validadas ni instrumentos certificados.
