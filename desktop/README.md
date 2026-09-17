Carpeta destinada al código fuente en Java desarrollado para la solución de escritorio.

## Kiosko de Autoatención (JavaFX)

Requisitos: JDK 21 o superior. Las dependencias (JavaFX, AtlantaFX, Jackson) las descarga Maven.

### Pruebas

```bash
cd desktop
./mvnw test
```

### Ejecutar el kiosko

Contra la API real (por defecto `http://localhost:8080/api/v1`):

```bash
./mvnw javafx:run
```

Apuntando a otra URL de la API:

```bash
./mvnw javafx:run -Dapi.url=http://localhost:8080/api/v1
```

Con la API simulada en memoria, sin necesidad de levantar la API ni PostgreSQL:

```bash
./mvnw javafx:run -Dkiosk.args="--demo-stub"
```

En pantalla completa (modo kiosko):

```bash
./mvnw javafx:run -Dkiosk.args="--kiosk"
```

Capturas de pantalla de todas las pantallas del flujo, para revisar el diseño:

```bash
./mvnw javafx:run -Dkiosk.args="--demo-stub --capture=/ruta/de/salida/"
```

Las capturas que ilustran la documentación viven en `docs/screens/img/` y se regeneran así, desde la raíz del repositorio:

```bash
./scripts/run-kiosk.sh --demo-stub --capture=docs/screens/img/
```

El documento que las muestra, pantalla por pantalla, es [`docs/screens.md`](../docs/screens.md).

### Ejecutable

La clase principal es `com.medicitas.kiosk.app.KioskLauncher` (propiedad `exec.mainClass` del `pom.xml`).
