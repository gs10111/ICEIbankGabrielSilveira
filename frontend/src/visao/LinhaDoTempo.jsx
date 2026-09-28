import { useMemo } from 'react'
import { paresConcorrentes } from '../modelo/causalidade.js'
import { hora, vetor } from '../modelo/formato.js'
import { Alerta, Blueprint, Tag } from './componentes/Base.jsx'

/**
 * VIEW — a linha do tempo das 3 agencias, agora com carimbo VETORIAL.
 *
 * No Sprint 1 esta tabela era ordenada pelo inteiro de Lamport e destacava os
 * EMPATES. Com vetor isso deixou de fazer sentido: vetores dao ordem PARCIAL, e
 * uma tabela impressa e necessariamente uma ordem total — nao existe "ordenar
 * pelo carimbo". A listagem passa a ser por hora de parede, que serve so para a
 * leitura, com o vetor visivel em cada linha. Quem responde "quem veio antes" e a
 * comparacao de vetores, que entra na Parte D.
 */
export default function LinhaDoTempo({ eventos, alerta, aoGerarConcorrentes }) {
  const ordenados = useMemo(
    () => [...eventos].sort((a, b) => a.horaParede.localeCompare(b.horaParede)),
    [eventos])

  // PARTE D. A analise nao vive aqui dentro: mora em modelo/causalidade.js, que e
  // funcao pura e tem teste. A tela so exibe o que ela devolve.
  const concorrentes = useMemo(() => paresConcorrentes(ordenados), [ordenados])

  return (
    <>
      <Alerta alerta={alerta} />
      <div className="card-kicker">java -jar agencia.jar --mesclar-logs</div>
      <h3>Linha do tempo unificada</h3>
      <p style={{ maxWidth: '78ch', color: 'var(--color-neutral-700)', marginTop: 'var(--space-3)' }}>
        Todos os eventos das três agências, com o <strong>carimbo vetorial</strong> de
        cada um — uma posição por agência. A ordem da tabela é a da hora de parede, e
        isso é só para a leitura: relógios físicos de máquinas diferentes não estão
        sincronizados e não carregam causalidade. Quem responde se dois eventos são
        concorrentes é a comparação posição a posição dos vetores.
      </p>

      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)',
        margin: 'var(--space-5) 0' }}>
        <Tag tipo="neutral">{ordenados.length} eventos</Tag>
        <Tag tipo="neutral">vetor de {ordenados[0]?.timestampVetorial?.length ?? 3} posições</Tag>
        <Tag tipo={concorrentes.length > 0 ? 'accent' : 'neutral'}>
          {concorrentes.length} pares concorrentes
        </Tag>
        <button className="btn btn-secondary" style={{ marginLeft: 'auto' }} onClick={aoGerarConcorrentes}>
          Gerar eventos concorrentes
        </button>
      </div>

      <table className="table">
        <thead>
          <tr>
            <th style={{ width: 90 }}>Vetor</th><th style={{ width: 80 }}>Agência</th>
            <th style={{ width: 230 }}>Tipo</th><th>Detalhes</th>
            <th style={{ width: 130, textAlign: 'right' }}>Hora de parede</th>
          </tr>
        </thead>
        <tbody>
          {ordenados.map((evento, indice) => (
            <tr key={indice}>
              <td className="num" style={{ fontFamily: 'var(--font-heading)', fontSize: 12,
                color: 'var(--color-accent-700)' }}>{vetor(evento.timestampVetorial)}</td>
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
        <div className="card-kicker">Parte D — pares concorrentes (agências diferentes)</div>
        <div style={{ fontFamily: 'var(--font-heading)', fontSize: 25, margin: 'var(--space-3) 0' }}>
          nenhum dos dois vetores domina o outro
        </div>
        <p style={{ maxWidth: '78ch', color: 'var(--color-neutral-700)', fontSize: 13 }}>
          Dois eventos são <strong>concorrentes</strong> quando cada um sabe de algo que o
          outro não sabe — não existe cadeia causal entre eles em nenhum sentido. Pares da
          mesma agência nunca aparecem aqui: dentro de uma agência há um relógio só, e ele
          sempre incrementa. Uma transferência entre agências também não aparece, e é esse
          o ponto: pela regra 3 o destino absorveu o vetor da origem, então há causalidade.
        </p>
        {concorrentes.length === 0 ? (
          <p className="text-muted" style={{ fontSize: 13 }}>
            Nenhum par concorrente entre estes eventos. Use “Gerar eventos concorrentes” para
            disparar operações independentes em agências diferentes.
          </p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Evento em uma agência</th><th>Evento na outra</th></tr>
            </thead>
            <tbody>
              {concorrentes.map(({ primeiro, segundo }, i) => (
                <tr key={i}>
                  <td>
                    <Tag tipo="neutral">AG&nbsp;{primeiro.agencia.replace('agencia-', '')}</Tag>{' '}
                    {primeiro.tipo}{' '}
                    <span className="num" style={{ color: 'var(--color-accent-700)' }}>
                      {vetor(primeiro.timestampVetorial)}
                    </span>
                  </td>
                  <td>
                    <Tag tipo="neutral">AG&nbsp;{segundo.agencia.replace('agencia-', '')}</Tag>{' '}
                    {segundo.tipo}{' '}
                    <span className="num" style={{ color: 'var(--color-accent-700)' }}>
                      {vetor(segundo.timestampVetorial)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Blueprint>

      <Blueprint style={{ padding: 'var(--space-6)', marginTop: 'var(--space-7)' }}>
        <div className="card-kicker">Regra 3 verificada nos créditos remotos</div>
        <div style={{ fontFamily: 'var(--font-heading)', fontSize: 25, margin: 'var(--space-3) 0' }}>
          vetor[i] ← max(vetor[i], recebido[i]), depois +1 na própria
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
                  <td className="num" style={{ fontSize: 12 }}>{vetor(e.timestampVetorial)}</td>
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
