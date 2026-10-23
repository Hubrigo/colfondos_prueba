import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App.jsx';
import './styles.css';

// Última línea de defensa (INC-001): una promesa rechazada sin manejo queda registrada
// con contexto en lugar de perderse como "Unhandled Promise Rejection".
window.addEventListener('unhandledrejection', (event) => {
  console.error('[contact-update] promesa rechazada sin manejo', event.reason);
});

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
