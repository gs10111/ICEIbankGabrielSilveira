import { AGENCIAS } from '../modelo/agencias.js'
import { Segmentado, Tag } from './componentes/Base.jsx'

const MENU = [
  { grupo: 'Conta', itens: [
    { id: 'painel', rotulo: 'Painel', endpoint: 'GET /contas/:id' },
    { id: 'historico', rotulo: 'Histórico', endpoint: 'GET /contas/:id/historico' },
    { id: 'extrato', rotulo: 'Extrato consolidado', endpoint: 'GET /extrato-consolidado' },
  ]},
  { grupo: 'Movimentações', itens: [
    { id: 'deposito', rotulo: 'Depósito', endpoint: 'POST /contas/:id/depositar' },
    { id: 'saque', rotulo: 'Saque', endpoint: 'POST /contas/:id/sacar' },
    { id: 'transferencia', rotulo: 'Transferência', endpoint: 'POST /transferencias' },
  ]},
  { grupo: 'Observabilidade', itens: [
    { id: 'linhaDoTempo', rotulo: 'Linha do tempo', endpoint: 'GET /eventos (3 agências)' },
  ]},
]

/** VIEW — moldura fixa: barra institucional, barra do produto e menu lateral. */
export default function Layout({ sessao, segundosRestantes, agenciaEntrada, aoTrocarAgencia,
                                 relogioDaAgencia, tela, aoNavegar, aoSair, aoExpirarToken, children }) {
  const tokenAcabando = segundosRestantes < 20

  return (
    <div>
      {/* barra institucional */}
      <div style={{ background: 'var(--color-accent-900)', color: 'var(--color-bg)',
        padding: 'var(--space-2) 0', fontSize: 12 }}>
        <div className="container" style={{ display: 'flex', alignItems: 'center',
          gap: 'var(--space-4)', padding: '0 var(--space-8)' }}>
          <span style={{ fontFamily: 'var(--font-heading)', textTransform: 'uppercase',
            letterSpacing: '0.16em', fontWeight: 600 }}>PUC Minas · ICEI</span>
          <span style={{ color: 'var(--color-accent-300)' }}>Portal do aluno</span>
          <span style={{ marginLeft: 'auto' }}>
            {sessao.nomeAluno} · matrícula {String(sessao.idConta).padStart(6, '0')}
          </span>
          <button className="btn btn-ghost" style={{ color: 'var(--color-bg)' }} onClick={aoSair}>Sair</button>
        </div>
      </div>

      {/* barra do produto */}
      <div style={{ borderBottom: '1px solid var(--color-divider)' }}>
        <div className="container nav" style={{ padding: 'var(--space-4) var(--space-8)' }}>
          <span className="nav-brand">ICEIBANK</span>

          <div style={{ width: 300 }}>
            <Segmentado nome="agenciaEntrada" valor={agenciaEntrada} aoMudar={aoTrocarAgencia}
              opcoes={AGENCIAS.map((a) => ({ valor: a.id, rotulo: `AG ${a.id}` }))} />
          </div>

          <Tag tipo="neutral">LAMPORT {relogioDaAgencia ?? '—'}</Tag>

          <span className="num" style={{ fontSize: 12,
            color: tokenAcabando ? 'var(--color-accent-800)' : 'var(--color-neutral-600)' }}>
            token {segundosRestantes}s
          </span>

          <button className="btn btn-secondary" style={{ marginLeft: 'auto' }} onClick={aoExpirarToken}>
            Expirar token
          </button>
        </div>
      </div>

      {/* corpo */}
      <div className="container" style={{ display: 'grid',
        gridTemplateColumns: '208px minmax(0, 1fr)', gap: 'calc(var(--space-8) * 1.3)',
        padding: 'var(--space-6) var(--space-8)' }}>

        <nav style={{ position: 'sticky', top: 'var(--space-4)', alignSelf: 'start' }}>
          {MENU.map((secao) => (
            <div key={secao.grupo} style={{ marginBottom: 'var(--space-5)' }}>
              <h6>{secao.grupo}</h6>
              <hr className="regua" style={{ margin: 'var(--space-2) 0 var(--space-3)' }} />
              {secao.itens.map((item) => {
                const ativo = tela === item.id
                return (
                  <button key={item.id} className="btn" onClick={() => aoNavegar(item.id)}
                    style={{ display: 'block', width: '100%', textAlign: 'left',
                      border: '1px solid transparent',
                      borderLeft: `2px solid ${ativo ? 'var(--color-accent)' : 'transparent'}`,
                      background: ativo ? 'var(--color-accent-100)' : 'transparent',
                      color: ativo ? 'var(--color-accent-700)' : 'var(--color-text)',
                      padding: 'var(--space-2) var(--space-3)', marginBottom: 2 }}>
                    {item.rotulo}
                    <span style={{ display: 'block', fontSize: 10, textTransform: 'none',
                      letterSpacing: 0, fontFamily: 'var(--font-body)',
                      color: 'var(--color-neutral-600)' }}>{item.endpoint}</span>
                  </button>
                )
              })}
            </div>
          ))}
        </nav>

        <main>{children}</main>
      </div>
    </div>
  )
}
