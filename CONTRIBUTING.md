# Guía de contribución

## Estrategia de ramas (Git Flow simplificado)

| Rama                              | Propósito                                                                 |
|-----------------------------------|---------------------------------------------------------------------------|
| `main`                            | Código liberado a producción. Solo recibe merges desde `release/*` o `hotfix/*`. |
| `develop`                         | Integración continua del trabajo del sprint.                             |
| `feature/HU-CONTACT-001-<paso>`   | Una rama por paso o tarea técnica; entra a `develop` por PR con `merge --no-ff`. |
| `release/<version>`               | Estabilización previa al comité de cambios (martes 11:00 AM, doc. 12).   |
| `hotfix/<incidente>`              | Corrección urgente sobre `main`; se despliega en la ventana posterior a las 9:00 PM. |

Reglas:
- No se hace commit directo a `main` ni a `develop`.
- Toda rama `feature/*` se integra por Pull Request con revisión de código.
- Antes del merge: compila, pruebas en verde y checklist del PR completo.

## Convención de commits

[Conventional Commits](https://www.conventionalcommits.org/) con referencia a la HU:

```
<tipo>(<alcance>): <descripción en imperativo>

<cuerpo opcional>

Refs: HU-CONTACT-001
AI-Assisted: <qué parte fue asistida por IA y cómo se validó>   # solo si aplica
```

Tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`, `build`, `ci`.
Alcances: `backend`, `frontend`, `docs`, `repo`.

El *trailer* `AI-Assisted:` alimenta el registro de uso de IA (E7, doc. 15).
