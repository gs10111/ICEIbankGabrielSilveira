import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import { descobrirMalha } from './modelo/agencias.js'
import './design-system.css'

// A topologia e perguntada ao backend ANTES do primeiro render. Assim nenhuma
// tela precisa lidar com "ainda nao sei quantas agencias existem", e AGENCIAS
// continua sendo leitura sincrona nos componentes.
descobrirMalha().then((malha) => {
  if (!malha.descoberta) {
    console.warn(
      `[iceibank] nao consegui ler a topologia do backend (${malha.motivo}); ` +
      `seguindo com ${malha.total} agencias. A malha do Painel mostra quem nao respondeu.`,
    )
  }

  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  )
})
