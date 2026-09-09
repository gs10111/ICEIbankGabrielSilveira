import { useState } from 'react'
import { dinheiro } from '../modelo/formato.js'
import { Alerta, Blueprint, Campo, Tag } from './componentes/Base.jsx'

/** VIEW — FUNCIONALIDADE ADICIONAL 3: soma saldos de contas em agencias diferentes. */
export default function Extrato({ contaPadrao, extrato, alerta, aoConsolidar }) {
  const [contas, setContas] = useState(`${contaPadrao},4`)

  return (
    <>
      <div className="card-kicker">GET /extrato-consolidado?contas=…</div>
      <h3>Extrato consolidado</h3>
      <Tag tipo="outline">FUNCIONALIDADE ADICIONAL · SEÇÃO 2.1</Tag>
      <p style={{ maxWidth: '70ch', color: 'var(--color-neutral-700)', marginTop: 'var(--space-3)' }}>
        Soma os saldos de várias contas do mesmo titular, mesmo espalhadas por agências
        diferentes. Para cada conta a agência decide, pela regra de partição, se busca
        localmente ou faz uma chamada remota. O total <strong>não</strong> é um snapshot
        atômico: as agências são lidas uma a uma.
      </p>

      <form style={{ display: 'flex', gap: 'var(--space-4)', alignItems: 'flex-end',
        margin: 'var(--space-5) 0' }}
        onSubmit={(e) => { e.preventDefault(); aoConsolidar(contas.split(',').map((c) => Number(c.trim()))) }}>
        <div style={{ width: 260 }}>
          <Campo rotulo="Contas (separadas por vírgula)" value={contas}
            onChange={(e) => setContas(e.target.value)} />
        </div>
        <button className="btn btn-primary" type="submit" style={{ marginBottom: 'var(--space-4)' }}>
          Consolidar
        </button>
      </form>

      <Alerta alerta={alerta} />

      {extrato ? (
        <>
          <Blueprint style={{ padding: 'var(--space-6)', marginBottom: 'var(--space-5)' }}>
            <div className="card-kicker">Total consolidado</div>
            <div className="valor-grande">{dinheiro(extrato.total)}</div>
            {!extrato.consistente ? (
              <div className="alerta erro" style={{ marginTop: 'var(--space-4)' }}>
                <div className="alerta-titulo">Resultado parcial</div>
                <div className="alerta-texto">
                  Alguma agência não respondeu. O total abaixo ignora as contas marcadas
                  como indisponíveis — leitura distribuída não é atômica.
                </div>
              </div>
            ) : null}
          </Blueprint>

          <table className="table">
            <thead><tr><th>Conta</th><th>Titular</th><th>Agência</th><th style={{ textAlign: 'right' }}>Saldo</th></tr></thead>
            <tbody>
              {extrato.contas.map((item) => (
                <tr key={item.id}>
                  <td className="num">{item.id}</td>
                  <td>{item.nomeAluno ?? <span className="text-muted">—</span>}</td>
                  <td><Tag tipo={item.disponivel ? 'neutral' : 'erro'}>AG&nbsp;{item.agencia}</Tag></td>
                  <td className="num" style={{ textAlign: 'right' }}>
                    {item.disponivel ? dinheiro(item.saldo) : <span className="text-muted">indisponível</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </>
  )
}
