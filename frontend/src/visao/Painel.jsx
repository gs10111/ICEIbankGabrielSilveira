import { AGENCIAS } from '../modelo/agencias.js'
import { dinheiro, hora, rotuloDoEvento, sinalDoEvento, valorDoEvento, vetor } from '../modelo/formato.js'
import { Blueprint, IconeDeposito, IconeSaque, IconeTransferencia, Tag } from './componentes/Base.jsx'

/** VIEW — saldo, ultimos lancamentos e a malha de agencias. */
export default function Painel({ conta, eventos, malha, agenciaEntrada, aoNavegar }) {
  return (
    <>
      <div style={{ display: 'grid', gridTemplateColumns: '1.3fr 1fr', gap: 'var(--space-6)' }}>
        <Blueprint style={{ padding: 'var(--space-6)', display: 'flex', flexDirection: 'column',
          justifyContent: 'space-between' }}>
          <div>
            <div className="card-kicker">Saldo disponível · conta {conta?.id ?? '—'}</div>
            <div className="valor-grande">{dinheiro(conta?.saldo)}</div>
            <div style={{ fontSize: 13, color: 'var(--color-neutral-700)', marginTop: 'var(--space-2)' }}>
              {conta?.nomeAluno} · agência {agenciaEntrada} · porta {AGENCIAS[agenciaEntrada].porta}
            </div>
          </div>

          <div>
            <hr className="regua" />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)' }}>
              {[
                { id: 'deposito', rotulo: 'Depositar', Icone: IconeDeposito },
                { id: 'saque', rotulo: 'Sacar', Icone: IconeSaque },
                { id: 'transferencia', rotulo: 'Transferir', Icone: IconeTransferencia },
              ].map(({ id, rotulo, Icone }, indice) => (
                <button key={id} className="btn" onClick={() => aoNavegar(id)}
                  style={{ border: 'none', borderLeft: indice ? '1px solid var(--color-divider)' : 'none',
                    textAlign: 'left', padding: 'var(--space-3) var(--space-4)' }}>
                  <Icone />
                  <span style={{ display: 'block', marginTop: 'var(--space-2)' }}>{rotulo}</span>
                </button>
              ))}
            </div>
          </div>
        </Blueprint>

        <Blueprint className="card" style={{ padding: 'var(--space-5)' }}>
          <div className="card-kicker">Últimos lançamentos</div>
          {eventos.length === 0
            ? <p className="text-muted" style={{ fontSize: 13 }}>Sem lançamentos nesta conta ainda.</p>
            : eventos.slice(0, 4).map((evento, indice) => {
                const sinal = sinalDoEvento(evento.tipo)
                return (
                  <div key={indice} style={{ display: 'flex', alignItems: 'baseline',
                    gap: 'var(--space-3)', padding: 'var(--space-2) 0',
                    borderBottom: '1px solid var(--color-divider)' }}>
                    <div style={{ flex: 1 }}>
                      <div className="card-title">{rotuloDoEvento(evento.tipo)}</div>
                      <div className="card-meta">
                        AG {evento.agencia.replace('agencia-', '')} · {vetor(evento.timestampVetorial)} · {hora(evento.horaParede)}
                      </div>
                    </div>
                    <div className="num" style={{ fontFamily: 'var(--font-heading)', fontSize: 17,
                      color: sinal === '+' ? 'var(--color-accent-700)' : 'var(--color-text)' }}>
                      {sinal} {dinheiro(valorDoEvento(evento.detalhes)).replace('R$', '').trim()}
                    </div>
                  </div>
                )
              })}
          <button className="btn btn-secondary" style={{ marginTop: 'var(--space-4)' }}
            onClick={() => aoNavegar('historico')}>Ver histórico completo</button>
        </Blueprint>
      </div>

      <h4 style={{ marginTop: 'var(--space-7)' }}>Malha de agências</h4>
      <p className="card-meta" style={{ marginBottom: 'var(--space-4)' }}>
        derrube uma agência (Ctrl+C no terminal dela) para reproduzir a falha conhecida da Parte D
      </p>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 'var(--space-4)' }}>
        {AGENCIAS.map((a) => {
          const status = malha[a.id]
          const noAr = Boolean(status)
          return (
            <Blueprint key={a.id} className="card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span className="card-kicker" style={{ margin: 0 }}>AGÊNCIA&nbsp;{a.id}</span>
                <span className={`tag ${noAr ? 'tag-outline' : 'tag-erro'}`}>
                  {noAr ? 'ATIVA' : 'FORA DO AR'}
                </span>
              </div>
              <div className="porta">:{a.porta}</div>
              <div className="card-body">
                contas sob responsabilidade: {status?.contas ?? '—'}<br />
                relógio vetorial: {vetor(status?.relogioVetorial)}<br />
                eventos registrados: {status?.eventos ?? '—'}
              </div>
            </Blueprint>
          )
        })}
      </div>
    </>
  )
}
