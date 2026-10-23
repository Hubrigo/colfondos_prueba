import { Component } from 'react';
import { missingConfig } from './config.js';
import { ContactUpdateWizard } from './wizard/ContactUpdateWizard.jsx';

/**
 * Red de seguridad para errores de render (INC-001): el afiliado ve un mensaje con
 * opción de reintentar en lugar de una pantalla en blanco.
 */
class ErrorBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error, info) {
    console.error('[contact-update] error de render', error, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="banner banner--error" role="alert">
        <p>Algo salió mal al mostrar esta pantalla.</p>
        <button type="button" className="btn" onClick={() => this.setState({ failed: false })}>Intentar de nuevo</button>
      </div>
    );
  }
}

export function App() {
  return (
    <>
      <header className="topbar">
        <span className="topbar__brand">Portal de afiliados</span>
        <span className="topbar__tag">Demo técnica · datos sintéticos</span>
      </header>
      <main className="container">
        {missingConfig.length > 0 ? (
          <div className="banner banner--error" role="alert">
            Configuración incompleta: falta {missingConfig.join(', ')}. Revisa el archivo .env (ver .env.example).
          </div>
        ) : (
          <ErrorBoundary>
            <ContactUpdateWizard />
          </ErrorBoundary>
        )}
      </main>
    </>
  );
}
