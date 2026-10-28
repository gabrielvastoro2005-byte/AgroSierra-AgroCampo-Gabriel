# AgroCampo — Android

**Estudiante:** Gabriel Vasquez Toro  
**Caso:** AgroSierra — Examen Parcial II, Unidad 2, Versión A

Aplicación Android en Kotlin Multiplatform. El módulo `shared` contiene la lógica común; `androidApp` implementa la aplicación Android y las capacidades nativas de almacenamiento cifrado, SQLite y conectividad.

## Ejecutar

1. Inicia primero el backend `AgroAPI` (ver el README del directorio hermano).
2. Abre esta carpeta en Android Studio y espera a que Gradle sincronice.
3. Selecciona un emulador Android y ejecuta la configuración `androidApp`.
4. Inicia sesión con `tecnico1` / `Agro2026*`, `jefe1` / `Agro2026*` o `admin` / `Agro2026*`.

El emulador usa `http://10.0.2.2:8081/api/v1/`; para un teléfono físico se debe configurar la dirección LAN de la computadora en `shared/src/commonMain/kotlin/pe/upeu/agrosierra/data/remote/AgroApi.kt`.

## Estructura

- `shared/src/commonMain/.../domain`: modelos, contratos de repositorio y casos de uso.
- `shared/src/commonMain/.../data`: DTO, Ktor, mapeo de errores, SQLDelight y sincronización offline-first.
- `shared/src/commonMain/.../presentation`: ViewModel, estado observable y acciones de la interfaz.
- `shared/src/commonMain/sqldelight`: esquema y consultas locales.
- `shared/src/commonTest`: pruebas comunes de conversión, errores, registro offline y orden de sincronización.
- `androidApp/src`: Compose y adaptadores nativos Android.

## Validación de build

En Windows, desde esta carpeta:

```powershell
./gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug
```

El APK de depuración se genera en `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

## Requisitos y evidencias

- Guía del examen: `docs/requisitos/examen_parcial_ii_practico_versiona.pdf`.
- Informe final: `docs/Informe_Final_AgroSierra_Gabriel_Vasquez_Toro.pdf`.
- Capturas Android del emulador: `evidencias/`.
- La ejecución verificada en esta entrega es Android; no se incluye evidencia de compilación o ejecución iOS.
