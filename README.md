# Reporte Basura

App Android mínima para reportar **basura fuera del contenedor**: sacás una foto, la app guarda dónde la sacaste (dirección + coordenadas GPS) y la manda por WhatsApp a un número configurado, con el mensaje
«Basura fuera del contenedor en _Cam. Servando Gómez 2280, Carrasco, Montevideo_».

## Qué hace

- **Sacar foto**: abre la cámara del teléfono y guarda la foto comprimida (lado mayor 1600 px, JPEG 80, ~200–400 KB).
- **Ubicación**: GPS y red con `LocationManager` (sin Google Play Services). El GPS se prende al tocar el botón, así ya tiene posición cuando volvés de la cámara; si no hay una buena, espera hasta 25 s y si no usa la última conocida o el GPS que haya grabado la cámara en la foto (EXIF).
- **Dirección**: geocodificación inversa con `android.location.Geocoder` («Calle 1234, Barrio, Ciudad»). Si no hay red, se guardan las coordenadas y la dirección se busca de nuevo al volver a abrir la app. También se puede escribir a mano.
- **Galería** con filtro *Todas / Pendientes / Enviadas*. Cada foto muestra **«Pendiente de reportar»** o **«Enviado»**.
- **Envío múltiple**: mantené apretada una foto para seleccionar varias (o *Seleccionar todas*), tocá **Enviar**: se manda **una foto por mensaje**, cada una con su dirección, de la más vieja a la más nueva. Al volver de WhatsApp queda marcada como enviada y pregunta si seguir (con «No se envió» para deshacer). Menú ⋮ → *Enviar todas las pendientes*.
- **Detalle** de cada foto: enviar, marcar enviada/pendiente, editar dirección, buscar la dirección de nuevo, ver en el mapa, borrar.
- **Ajustes**: número destino (acepta `098 828 017`, `98828017`, `+598 98 828 017`; los números uruguayos se pasan solos a +598), texto del mensaje con `{direccion}`, enlace a Google Maps opcional y elección entre WhatsApp / WhatsApp Business.

## Limitación de WhatsApp (importante)

WhatsApp **no tiene una forma oficial** de mandar una foto directo a un número:

- Los enlaces `wa.me/<número>?text=...` abren el chat correcto pero **no admiten adjuntos**.
- Por eso la app usa `Intent.ACTION_SEND` dirigido al paquete `com.whatsapp` (o `com.whatsapp.w4b`), con la foto (`EXTRA_STREAM`, por un `content://` con permiso temporal), el texto como pie de foto (`EXTRA_TEXT`) y el extra **no documentado** `jid = <número>@s.whatsapp.net`.
- En la mayoría de las versiones eso abre directamente el chat con ese número y la foto lista para mandar (hay que tocar ➤ enviar: ninguna app puede mandar mensajes de WhatsApp sola). Si WhatsApp ignora el `jid` (algunas versiones, o si el número no está agendado / no usa WhatsApp), aparece la lista de chats y elegís el destino a mano; la foto y el texto ya van cargados. **Conviene agendar el número destino.**
- WhatsApp no le avisa a la app si el mensaje se mandó o se canceló; por eso al volver se marca como «Enviado» y se ofrece deshacer.
- Sin WhatsApp instalado se abre el selector de compartir de Android.

## Por qué es tan liviana

| Decisión | Motivo |
|---|---|
| Kotlin + **Views del sistema** (sin Compose, sin AppCompat ni ninguna librería androidx) | Compose suma ~1–2 MB aun con R8; AppCompat/Material ~1 MB. Con `android.app.Activity`, `GridView` y el tema Material del sistema la app pesa decenas de KB. |
| Cámara por `ACTION_IMAGE_CAPTURE` | Usa la app de cámara del teléfono: no hace falta CameraX ni el permiso `CAMERA`. |
| `LocationManager` + `Geocoder` | Sin Google Play Services (funciona también en teléfonos sin Google). |
| `FotoProvider` propio (40 líneas) | Reemplaza a `androidx.core.content.FileProvider`. |
| JSON (`org.json` + `AtomicFile`) | Sin Room ni SQLite: son pocos registros. |
| R8 (`isMinifyEnabled`) + `isShrinkResources` | Recorta la librería estándar de Kotlin y los recursos sin uso. |
| Sin permiso de Internet | La geocodificación la hace el servicio del sistema. |

Resultado: **APK release de ~45 KB** (v1.0). Requisitos: Android 7.0 (API 24) o superior.

## Compilar (Windows)

### 1. Herramientas (gratis)

- **JDK 17 o superior** (en esta PC ya está `C:\Program Files\Java\jdk-20`).
- **Android SDK** sin Android Studio (alcanza con las *command-line tools*):
  1. Bajá «Command line tools only» para Windows de <https://developer.android.com/studio#command-line-tools-only>.
  2. Descomprimí para que quede `C:\Users\<usuario>\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat`.
  3. En PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-20"
```

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

  4. Creá `local.properties` en la raíz del proyecto (no va a git) con:
     `sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk`

  (Alternativa: instalar Android Studio, que trae todo.)

### 2. Generar la APK

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-20"
```

```powershell
.\gradlew.bat assembleRelease
```

La APK queda en `app\build\outputs\apk\release\app-release.apk`.
La primera vez Gradle baja sus dependencias (unos minutos).

**Firma**: si no existe `keystore.properties`, el release se firma con la llave de *debug* de la PC (sirve para instalar, no para Google Play). Para una llave propia:

```powershell
keytool -genkeypair -v -keystore reporte-basura.jks -alias reporte -keyalg RSA -keysize 2048 -validity 10000
```

y creá `keystore.properties` (no va a git) con `storeFile=reporte-basura.jks`, `storePassword=...`, `keyAlias=reporte`, `keyPassword=...`. Guardá una copia de la llave: sin ella no se pueden instalar actualizaciones encima. Si cambiás de llave, hay que desinstalar la versión anterior (se pierden las fotos guardadas).

## Instalar en el teléfono

- **Por cable**: activá *Opciones de desarrollador → Depuración USB*, conectá el teléfono y:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
```

- **Sin cable**: pasá el `.apk` al teléfono (Drive, WhatsApp a vos mismo, USB), abrilo y permití «Instalar apps desconocidas» para esa app.

## Primer uso

1. ⋮ → **Ajustes**: poné el número destino (ej. el de la Intendencia o el vecinal) y guardá.
2. Tocá **Sacar foto**, aceptá el permiso de ubicación y sacá la foto.
3. Tocá la foto → **Enviar por WhatsApp**, o seleccioná varias y **Enviar**.

## Estructura

```
app/src/main/java/uy/reportebasura/
  MainActivity.kt    galería, cámara, espera de GPS, cola de envío
  FotoActivity.kt    detalle de una foto
  AjustesActivity.kt número, mensaje, app de WhatsApp
  Repo.kt            modelo Reporte + JSON en files/ (fotos en files/fotos, miniaturas en files/miniaturas)
  Imagenes.kt        compresión/rotación de fotos y caché de miniaturas
  Ubicacion.kt       LocationManager + Geocoder
  WhatsApp.kt        ajustes, armado del mensaje e intent de envío
  FotoProvider.kt    content:// para la cámara y WhatsApp
```

Las fotos quedan en el almacenamiento privado de la app (no aparecen en la galería del teléfono) y se borran si se desinstala la app.
