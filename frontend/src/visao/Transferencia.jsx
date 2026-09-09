import { useMemo, useState } from 'react'
import { AGENCIAS, agenciaResponsavel } from '../modelo/agencias.js'
import { Alerta, Blueprint, Campo, Tag } from './componentes/Base.jsx'

/** VIEW — transferencia local e entre agencias, com a rota calculada em tempo real. */
export default function Transferencia({ contaPadrao, agenciaEntrada, malha, alerta, aoConfirmar }) {
  const [idOrigem, setIdOrigem] = useState(String(contaPadrao))
  const [idDestino, setIdDestino] = useState('')
  const [valor, setValor] = useState('')
  const [usarIdempotencia, setUsarIdempotencia] = useState(true)
  const [chave, setChave] = useState(() => `op-${Date.now()}`)
  const [enviando, setEnviando] = useState(false)

  const rota = useMemo(() => {
    const destino = Number(idDestino)
    if (!idDestino || Number.isNaN(destino) || destino < 0) return null

    const agenciaDestino = agenciaResponsavel(destino)
    if (agenciaDestino === agenciaEntrada) {
      return { tipo: 'local',
        texto: `local — destino na própria agência ${agenciaEntrada} (dois eventos locais, sem envio/recebimento)` }
    }
    const noAr = Boolean(malha[agenciaDestino])
    const alvo = `POST ${AGENCIAS[agenciaDestino].url}/contas/${destino}/creditar-remoto`
    return {
      tipo: noAr ? 'remota' : 'remotaCaida',
      texto: noAr ? `entre agências — ${alvo}`
                  : `entre agências — ${alvo} · destino FORA DO AR: 502 e débito não revertido`,
    }
  }, [idDestino, agenciaEntrada, malha])

  async function submeter(evento) {
    evento.preventDefault()
    setEnviando(true)
    try {
      await aoConfirmar(
        { idOrigem: Number(idOrigem), idDestino: Number(idDestino), valor },
        usarIdempotencia ? chave : null)
      setChave(`op-${Date.now()}`)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '330px minmax(0, 1fr)', gap: 'var(--space-8)' }}>
      <Blueprint style={{ padding: 'var(--space-6)', alignSelf: 'start' }}>
        <div className="card-kicker">POST /transferencias</div>
        <h3 style={{ marginBottom: 'var(--space-5)' }}>Transferência</h3>

        <form onSubmit={submeter}>
          <Campo rotulo="Conta de origem" value={idOrigem} inputMode="numeric"
            onChange={(e) => setIdOrigem(e.target.value)} />
          <Campo rotulo="Conta de destino" value={idDestino} inputMode="numeric"
            onChange={(e) => setIdDestino(e.target.value)} />
          <Campo rotulo="Valor (R$)" value={valor} inputMode="decimal" placeholder="0,00"
            onChange={(e) => setValor(e.target.value)} />

          {rota ? (
            <div style={{ borderLeft: '2px solid var(--color-accent-300)',
              paddingLeft: 'var(--space-4)', margin: 'var(--space-4) 0' }}>
              <div className="card-kicker" style={{ margin: 0 }}>Rota calculada</div>
              <div style={{ fontSize: 12, color: 'var(--color-neutral-700)', wordBreak: 'break-all' }}>
                {rota.texto}
              </div>
            </div>
          ) : null}

          {/* FUNCIONALIDADE ADICIONAL 2 — chave enviada no header Idempotency-Key. */}
          <label style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center',
            fontSize: 12, margin: 'var(--space-4) 0' }}>
            <input type="checkbox" checked={usarIdempotencia}
              onChange={(e) => setUsarIdempotencia(e.target.checked)} />
            usar chave de idempotência
          </label>
          {usarIdempotencia ? (
            <Campo rotulo="Idempotency-Key" value={chave} onChange={(e) => setChave(e.target.value)} />
          ) : null}

          <Alerta alerta={alerta} />
          <button className="btn btn-primary btn-block" type="submit" disabled={enviando}>
            {enviando ? 'Enviando…' : 'Transferir'}
          </button>
        </form>
      </Blueprint>

      <div>
        <h4 style={{ marginBottom: 'var(--space-4)' }}>Regra de partição</h4>
        <table className="table">
          <thead><tr><th>Conta</th><th>Cálculo</th><th>Agência responsável</th><th>Porta</th></tr></thead>
          <tbody>
            {[0, 1, 2, 3, 4, 5].map((conta) => {
              const dona = agenciaResponsavel(conta)
              return (
                <tr key={conta}>
                  <td className="num">{conta}</td>
                  <td className="num text-muted">{conta} mod 3 = {dona}</td>
                  <td><Tag tipo="neutral">AG&nbsp;{dona}</Tag></td>
                  <td className="num">:{AGENCIAS[dona].porta}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
        <hr className="regua" />
        <div style={{ fontFamily: 'var(--font-heading)', fontSize: 20 }}>
          agência responsável = id_conta mod 3
        </div>
      </div>
    </div>
  )
}
