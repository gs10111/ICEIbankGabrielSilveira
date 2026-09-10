import { useMemo } from 'react'
import { hora } from '../modelo/formato.js'
import { Alerta, Blueprint, Tag } from './componentes/Base.jsx'

/**
 * VIEW — PARTE E vista pela interface: eventos das 3 agencias ordenados por Lamport.
 * Linhas com timestamp repetido ficam destacadas: sao eventos CONCORRENTES.
 */
export default function LinhaDoTempo({ eventos, alerta, aoGerarConcorrentes }) {
  const { ordenados, empates } = useMemo(() => {
    const lista = [...eventos].sort((a, b) =>
      a.timestampLamport - b.timestampLamport || a.horaParede.localeCompare(b.horaParede))

    const contagem = new Map()
    lista.forEach((e) => contagem.set(e.timestampLamport, (contagem.get(e.timestampLamport) ?? 0) + 1))
    const empatados = [...contagem.entries()].filter(([, n]) => n > 1).map(([ts]) => ts)

    return { ordenados: lista, empates: new Set(empatados) }
  }, [eventos])

  return (
    <>
      <Alerta alerta={alerta} />
      <div className="card-kicker">java -jar agencia.jar --mesclar-logs</div>
      <h3>Linha do tempo unificada</h3>
      <p style={{ maxWidth: '78ch', color: 'var(--color-neutral-700)', marginTop: 'var(--space-3)' }}>
        Todos os eventos das três agências ordenados pelo relógio lógico. Timestamps
        repetidos (destacados) são eventos <strong>concorrentes</strong>: nenhum causou o
        outro, e Lamport — por construção — não consegue ordená-los. A hora de parede
        sugere uma ordem, mas relógios físicos de máquinas diferentes não estão
        sincronizados e não carregam causalidade.
      </p>

      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
        margin: 'var(--space-5) 0' }}>
        <Tag tipo="neutral">{ordenados.length} eventos</Tag>
        <Tag tipo="neutral">{empates.size} empates de Lamport</Tag>
        <button className="btn btn-secondary" style={{ marginLeft: 'auto' }} onClick={aoGerarConcorrentes}>
          Gerar eventos concorrentes
        </button>
      </div>

      <table className="table">
        <thead>
          <tr>
            <th style={{ width: 50 }}>L</th><th style={{ width: 80 }}>Agência</th>
            <th style={{ width: 230 }}>Tipo</th><th>Detalhes</th>
            <th style={{ width: 130, textAlign: 'right' }}>Hora de parede</th>
          </tr>
        </thead>
        <tbody>
          {ordenados.map((evento, indice) => (
            <tr key={indice} className={empates.has(evento.timestampLamport) ? 'empate' : ''}>
              <td className="num" style={{ fontFamily: 'var(--font-heading)',
                color: 'var(--color-accent-700)' }}>{evento.timestampLamport}</td>
              <td><Tag tipo="neutral">AG&nbsp;{evento.agencia.replace('agencia-', '')}</Tag></td>
              <td><Tag tipo="accent">{evento.tipo}</Tag></td>
              <td style={{ fontSize: 12, color: 'var(--color-neutral-600)', wordBreak: 'break-all' }}>
                {JSON.stringify(evento.detalhes)}
              </td>
              <td className="num" style={{ fontSize: 11, textAlign: 'right' }}>{hora(evento.horaParede)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <Blueprint style={{ padding: 'var(--space-6)', marginTop: 'var(--space-7)' }}>
        <div className="card-kicker">Regra 3 verificada nos créditos remotos</div>
        <div style={{ fontFamily: 'var(--font-heading)', fontSize: 25, margin: 'var(--space-3) 0' }}>
          contador ← max(local, t recebido) + 1
        </div>
        {ordenados.filter((e) => e.tipo === 'TRANSFERENCIA_CREDITO_REMOTO').length === 0 ? (
          <p className="text-muted" style={{ fontSize: 13 }}>
            Faça uma transferência entre agências diferentes para reunir o primeiro caso.
          </p>
        ) : (
          <table className="table">
            <thead><tr><th>Destino</th><th>Carimbo resultante</th><th>Origem</th><th>Detalhes</th></tr></thead>
            <tbody>
              {ordenados.filter((e) => e.tipo === 'TRANSFERENCIA_CREDITO_REMOTO').map((e, i) => (
                <tr key={i}>
                  <td><Tag tipo="neutral">AG&nbsp;{e.agencia.replace('agencia-', '')}</Tag></td>
                  <td className="num">{e.timestampLamport}</td>
                  <td className="num">AG {e.detalhes.agenciaOrigem}</td>
                  <td style={{ fontSize: 12 }} className="text-muted">{JSON.stringify(e.detalhes)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Blueprint>
    </>
  )
}
