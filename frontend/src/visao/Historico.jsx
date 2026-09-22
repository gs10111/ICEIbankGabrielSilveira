import { useState } from 'react'
import { hora, vetor } from '../modelo/formato.js'
import { Alerta, Tag } from './componentes/Base.jsx'

/** VIEW — FUNCIONALIDADE ADICIONAL 1: historico de eventos por conta. */
export default function Historico({ contaPadrao, eventos, alerta, aoBuscar }) {
  const [idConta, setIdConta] = useState(String(contaPadrao))

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 'var(--space-5)' }}>
        <div style={{ flex: 1 }}>
          <div className="card-kicker">GET /contas/:id/historico</div>
          <h3>Histórico da conta</h3>
          <Tag tipo="outline">FUNCIONALIDADE ADICIONAL · SEÇÃO 2.1</Tag>
        </div>
        <form style={{ width: 140 }} onSubmit={(e) => { e.preventDefault(); aoBuscar(Number(idConta)) }}>
          <label className="field" style={{ margin: 0 }}>
            <span style={{ display: 'block', fontSize: 11, fontWeight: 600, textTransform: 'uppercase',
              letterSpacing: '0.12em', color: 'var(--color-neutral-700)', marginBottom: 'var(--space-2)' }}>
              Conta
            </span>
            <input className="input" value={idConta} inputMode="numeric"
              onChange={(e) => setIdConta(e.target.value)}
              onBlur={() => aoBuscar(Number(idConta))} />
          </label>
        </form>
      </div>

      <Alerta alerta={alerta} />

      <table className="table" style={{ marginTop: 'var(--space-5)' }}>
        <thead>
          <tr>
            <th style={{ width: 50 }}>L</th><th style={{ width: 80 }}>Agência</th>
            <th style={{ width: 230 }}>Tipo</th><th>Detalhes</th>
            <th style={{ width: 130, textAlign: 'right' }}>Hora de parede</th>
          </tr>
        </thead>
        <tbody>
          {eventos.length === 0 ? (
            <tr><td colSpan={5} className="text-muted">Nenhum evento para esta conta.</td></tr>
          ) : eventos.map((evento, indice) => (
            <tr key={indice}>
              <td className="num" style={{ fontFamily: 'var(--font-heading)',
                color: 'var(--color-accent-700)', fontSize: 12 }}>{vetor(evento.timestampVetorial)}</td>
              <td><Tag tipo="neutral">AG&nbsp;{evento.agencia.replace('agencia-', '')}</Tag></td>
              <td><Tag tipo="accent">{evento.tipo}</Tag></td>
              <td style={{ fontSize: 12, color: 'var(--color-neutral-600)' }}>
                {JSON.stringify(evento.detalhes)}
              </td>
              <td className="num" style={{ fontSize: 11, textAlign: 'right' }}>{hora(evento.horaParede)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
