import { useEffect, useState } from 'react'
import { AGENCIAS, agenciaResponsavel } from '../modelo/agencias.js'
import { api } from '../modelo/api.js'
import { Alerta, Blueprint, Campo, Segmentado } from './componentes/Base.jsx'
import { useAlerta } from '../controle/useAlerta.js'

/** VIEW — tela de login e escolha da agencia de entrada. */
export default function Login({ aoEntrar }) {
  const [agencia, setAgencia] = useState(0)
  const [idConta, setIdConta] = useState('0')
  const [senha, setSenha] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [malha, setMalha] = useState({})
  const { alerta, doErro, limpar } = useAlerta()

  useEffect(() => {
    AGENCIAS.forEach((a) =>
      api.status(a.id)
        .then((s) => setMalha((atual) => ({ ...atual, [a.id]: s })))
        .catch(() => setMalha((atual) => ({ ...atual, [a.id]: null }))))
  }, [])

  async function submeter(evento) {
    evento.preventDefault()
    limpar()
    const conta = Number(idConta)

    // Checagem no CLIENTE so para dar mensagem melhor: o backend repete a validacao.
    if (!Number.isInteger(conta) || conta < 0) {
      doErro({ http: 400, message: 'Número de conta inválido' })
      return
    }
    const dona = agenciaResponsavel(conta)
    if (dona !== agencia) {
      doErro(Object.assign(new Error(
        `Conta ${conta} pertence à agência ${dona} (porta ${AGENCIAS[dona].porta}). ` +
        `Cada conta entra apenas pela agência dona dela: ${conta} mod 3 = ${dona}.`), { http: 400 }))
      return
    }

    setEnviando(true)
    try {
      await aoEntrar(agencia, conta, senha)
    } catch (erro) {
      doErro(erro)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div style={{ position: 'relative', minHeight: '100vh' }}>
      <div className="grade" />
      <div className="container" style={{ position: 'relative', zIndex: 1, display: 'grid',
        gridTemplateColumns: '1fr 1fr', gap: 'var(--space-8)', alignItems: 'center', minHeight: '100vh',
        padding: 'var(--space-8)' }}>

        <div>
          <h1>ICEIBANK</h1>
          <p style={{ maxWidth: '42ch', marginTop: 'var(--space-4)', color: 'var(--color-neutral-700)' }}>
            Banco particionado em três agências independentes. Toda operação carimbada
            com relógio lógico de Lamport.
          </p>

          <div style={{ maxWidth: 520, marginTop: 'var(--space-7)', border: '1px solid var(--color-divider)',
            display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)' }}>
            {AGENCIAS.map((a, indice) => (
              <div key={a.id} style={{ padding: 'var(--space-4)',
                borderLeft: indice ? '1px solid var(--color-divider)' : 'none' }}>
                <div className="card-kicker">AGÊNCIA&nbsp;{a.id}</div>
                <div className="porta">:{a.porta}</div>
                <div className="card-meta">
                  {malha[a.id] === null
                    ? 'fora do ar'
                    : malha[a.id]
                      ? `${malha[a.id].contas} contas · Lamport ${malha[a.id].relogioLamport}`
                      : '…'}
                </div>
              </div>
            ))}
          </div>
        </div>

        <Blueprint style={{ width: 372, justifySelf: 'end', padding: 'var(--space-8)',
          background: 'var(--color-bg)' }}>
          <div className="card-kicker">POST /auth/login</div>
          <h3 style={{ marginBottom: 'var(--space-5)' }}>Acesso à conta</h3>

          <form onSubmit={submeter}>
            <div style={{ marginBottom: 'var(--space-5)' }}>
              <Segmentado
                nome="agencia"
                valor={agencia}
                aoMudar={setAgencia}
                opcoes={AGENCIAS.map((a) => ({ valor: a.id, rotulo: `AG ${a.id} · :${a.porta}` }))}
              />
            </div>

            <Campo rotulo="Matrícula / número da conta" value={idConta} inputMode="numeric"
              onChange={(e) => setIdConta(e.target.value)} />
            <Campo rotulo="Senha" type="password" value={senha}
              onChange={(e) => setSenha(e.target.value)} />

            <Alerta alerta={alerta} />

            <button className="btn btn-primary btn-block" type="submit" disabled={enviando}>
              {enviando ? 'Entrando…' : 'Entrar'}
            </button>
          </form>

          <hr className="regua" />
          <div style={{ fontSize: 11, color: 'var(--color-neutral-600)' }}>
            <strong>Contas de demonstração:</strong><br />
            0 Ana Souza / ana123 (AG 0) · 1 Bruno Lima / bruno123 (AG 1)<br />
            2 Carla Dias / carla123 (AG 2) · 3 Diego Melo / diego123 (AG 0)<br />
            4 Ana Souza / ana123 (AG 1) · 5 Bruno Lima / bruno123 (AG 2)<br /><br />
            Cada conta entra <strong>somente</strong> pela agência dona dela (id mod 3).
          </div>
        </Blueprint>
      </div>
    </div>
  )
}
