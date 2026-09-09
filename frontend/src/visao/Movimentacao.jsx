import { useState } from 'react'
import { hora } from '../modelo/formato.js'
import { Alerta, Blueprint, Campo, Tag } from './componentes/Base.jsx'

/** VIEW — deposito e saque, parametrizados pelo tipo. */
export default function Movimentacao({ tipo, contaPadrao, eventosDaAgencia, alerta, aoConfirmar }) {
  const eSaque = tipo === 'saque'
  const [idConta, setIdConta] = useState(String(contaPadrao))
  const [valor, setValor] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function submeter(evento) {
    evento.preventDefault()
    setEnviando(true)
    try {
      await aoConfirmar(Number(idConta), valor)
      setValor('')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '330px minmax(0, 1fr)', gap: 'var(--space-8)' }}>
      <Blueprint style={{ padding: 'var(--space-6)', alignSelf: 'start' }}>
        <div className="card-kicker">POST /contas/:id/{eSaque ? 'sacar' : 'depositar'}</div>
        <h3 style={{ marginBottom: 'var(--space-5)' }}>{eSaque ? 'Saque' : 'Depósito'}</h3>

        <form onSubmit={submeter}>
          <Campo rotulo="Conta" value={idConta} inputMode="numeric"
            onChange={(e) => setIdConta(e.target.value)} />
          <Campo rotulo="Valor (R$)" value={valor} inputMode="decimal" placeholder="0,00"
            onChange={(e) => setValor(e.target.value)} />
          <Alerta alerta={alerta} />
          <button className="btn btn-primary btn-block" type="submit" disabled={enviando}>
            {enviando ? 'Enviando…' : (eSaque ? 'Sacar' : 'Depositar')}
          </button>
        </form>
      </Blueprint>

      <div>
        <h4 style={{ marginBottom: 'var(--space-4)' }}>Eventos recentes desta agência</h4>
        {eventosDaAgencia.length === 0
          ? <p className="text-muted">Nenhum evento registrado ainda.</p>
          : (
            <table className="table">
              <thead><tr><th style={{ width: 50 }}>L</th><th style={{ width: 210 }}>Tipo</th><th>Detalhes</th></tr></thead>
              <tbody>
                {eventosDaAgencia.slice(0, 6).map((evento, indice) => (
                  <tr key={indice}>
                    <td className="num" style={{ fontFamily: 'var(--font-heading)',
                      color: 'var(--color-accent-700)' }}>{evento.timestampLamport}</td>
                    <td><Tag tipo="accent">{evento.tipo}</Tag></td>
                    <td style={{ fontSize: 12, color: 'var(--color-neutral-600)' }}>
                      {JSON.stringify(evento.detalhes)}
                      <div className="card-meta">{hora(evento.horaParede)}</div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </div>
    </div>
  )
}
