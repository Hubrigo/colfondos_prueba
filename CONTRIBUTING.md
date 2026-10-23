# Guía de contribución

## Estrategia de ramas (Git Flow simplificado)

| Rama                              | Propósito                                                                 |
|-----------------------------------|---------------------------------------------------------------------------|
| `main`                            | Código liberado a producción. Solo recibe merges desde `release/*` o `hotfix/*`. |
| `develop`                         | Integración continua del trabajo del sprint.                             |
| `feature/HU-CONTACT-001-<paso>`   | Una rama por paso o tarea técnica; entra a `develop` por PR con `merge --no-ff`. |
| `bugfix/HU-CONTACT-001-<defecto>` | Corrección de un defecto reportado por QA antes de liberar; sale de `develop` y vuelve por PR. |
| `release/<version>`               | Estabilización previa al comité de cambios (martes 11:00 AM, doc. 12).   |
| `hotfix/<incidente>`              | Corrección urgente sobre `main`; se despliega en la ventana posterior a las 9:00 PM. |

Reglas:
- No se hace commit directo a `main` ni a `develop`.
- Toda rama `feature/*` se integra por Pull Request con revisión de código.
- Antes del merge: compila, pruebas en verde y checklist del PR completo.

### Protección de ramas (configurada en GitHub)

| Regla | `main` | `develop` |
|-------|--------|-----------|
| PR obligatorio (sin push directo) | ✅ | ✅ |
| Aplica también a administradores | ✅ | ✅ |
| Force-push / borrado de la rama | ❌ | ❌ |
| Conversaciones resueltas antes del merge | ✅ | ✅ |
| CI obligatorio: `Backend · mvn verify`, `Frontend · lint + Vitest + build`, `E2E · Chromium, Edge, WebKit, safari17` | ✅ | ✅ |
| Rama al día con la base antes del merge (*strict*) | ✅ | ✅ |
| Estrategia de merge permitida | solo *merge commit* (equivale a `--no-ff`) | igual |

> Aprobaciones requeridas: **0** en este repositorio de prueba, porque hay un único autor y GitHub no permite aprobar el PR propio. En el equipo real serían **≥ 1 revisor**, más el escaneo de ciberseguridad antes de producción (doc. 08).

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
