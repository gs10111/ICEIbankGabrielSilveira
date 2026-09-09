import { useCallback, useEffect, useState } from 'react'
import { AGENCIAS } from './modelo/agencias.js'
import { api, ErroDaApi } from './modelo/api.js'
import { useAlerta } from './controle/useAlerta.js'
import { useAutenticacao } from './controle/useAutenticacao.js'
import Extrato from './visao/Extrato.jsx'
import Historico from './visao/Historico.jsx'
import Layout from './visao/Layout.jsx'
import LinhaDoTempo from './visao/LinhaDoTempo.jsx'
import Login from './visao/Login.jsx'
import Movimentacao from './visao/Movimentacao.jsx'
import Painel from './visao/Painel.jsx'
import Transferencia from './visao/Transferencia.jsx'

/**
 * CONTROLLER principal — orquestra estado, chamadas e navegacao.
 *
 * MVC (pergunta 12.3.3):
 *   Model      -> src/modelo/  (api.js, agencias.js, formato.js)
 *   View       -> src/visao/   (componentes de apresentacao, sem fetch)
 *   Controller -> src/controle/ + este arquivo (estado, chamadas, traducao de erro)
 */
export default function App() {
  const { sessao, segundosRestantes, entrar, sair, expirarAgora } = useAutenticacao()
  const { alerta, sucesso, doErro, limpar } = useAlerta()

  const [agenciaEntrada, setAgenciaEntrada] = useState(0)
  const [tela, setTela] = useState('painel')
  const [conta, setConta] = useState(null)
  const [malha, setMalha] = useState({})
  const [eventosDaConta, setEventosDaConta] = useState([])
  const [eventosDaAgencia, setEventosDaAgencia] = useState([])
  const [eventosGlobais, setEventosGlobais] = useState([])
  const [extrato, setExtrato] = useState(null)

  useEffect(() => {
    if (sessao) setAgenciaEntrada(sessao.agencia)
  }, [sessao])

  /** 401 em qualquer chamada derruba a sessao COM aviso em tela (pergunta 12.3.2). */
  const tratar = useCallback((erro) => {
    if (erro instanceof ErroDaApi && erro.http === 401) {
      doErro(new ErroDaApi(401, 'Seu token expirou. Faça login novamente para continuar.'))
      setTimeout(sair, 2500)
      return
    }
    doErro(erro)
  }, [doErro, sair])

  const carregarMalha = useCallback(() => {
    AGENCIAS.forEach((a) =>
      api.status(a.id)
        .then((s) => setMalha((atual) => ({ ...atual, [a.id]: s })))
        .catch(() => setMalha((atual) => ({ ...atual, [a.id]: null }))))
  }, [])

  const recarregar = useCallback(async () => {
    if (!sessao) return
    carregarMalha()
    try {
      setConta(await api.consultarConta(sessao.agencia, sessao.idConta))
      setEventosDaConta(await api.historico(sessao.agencia, sessao.idConta, 30))
    } catch (erro) {
      tratar(erro)
    }
    try {
      setEventosDaAgencia(await api.eventos(agenciaEntrada, 30))
    } catch { /* agencia de entrada pode estar fora do ar; a malha ja mostra isso */ }
  }, [sessao, agenciaEntrada, carregarMalha, tratar])

  useEffect(() => { recarregar() }, [recarregar])

  useEffect(() => {
    if (!sessao || tela !== 'linhaDoTempo') return
    Promise.all(AGENCIAS.map((a) => api.eventos(a.id, 60).catch(() => [])))
      .then((listas) => setEventosGlobais(listas.flat()))
  }, [sessao, tela])

  if (!sessao) {
    return <Login aoEntrar={async (ag, id, senha) => { await entrar(ag, id, senha); setAgenciaEntrada(ag) }} />
  }

  async function movimentar(operacao, idConta, valorTexto) {
    limpar()
    const valor = Number(String(valorTexto).replace(',', '.'))
    if (!Number.isFinite(valor) || valor <= 0) {
      doErro(new ErroDaApi(400, 'Informe um valor numérico maior que zero.'))
      return
    }
    try {
      const atualizada = await api[operacao](agenciaEntrada, idConta, valor)
      sucesso(operacao === 'sacar' ? 'Saque efetuado' : 'Depósito efetuado',
        `Novo saldo da conta ${atualizada.id}: R$ ${atualizada.saldo}`)
      await recarregar()
    } catch (erro) {
      tratar(erro)
    }
  }

  async function transferir(ordem, chave) {
    limpar()
    const valor = Number(String(ordem.valor).replace(',', '.'))
    if (!Number.isFinite(valor) || valor <= 0) {
      doErro(new ErroDaApi(400, 'Informe um valor numérico maior que zero.'))
      return
    }
    try {
      const recibo = await api.transferir(agenciaEntrada, { ...ordem, valor }, chave)
      sucesso(recibo.reenvio ? 'Reenvio detectado (idempotência)' : 'Transferência concluída',
        `${recibo.mensagem} Saldo da origem: R$ ${recibo.saldoOrigem}` +
        (recibo.reenvio ? ' — a operação NÃO foi aplicada de novo.' : ''))
      await recarregar()
    } catch (erro) {
      tratar(erro)
    }
  }

  const telas = {
    painel: <Painel conta={conta} eventos={eventosDaConta} malha={malha}
              agenciaEntrada={agenciaEntrada} aoNavegar={setTela} />,
    deposito: <Movimentacao tipo="deposito" contaPadrao={sessao.idConta} eventosDaAgencia={eventosDaAgencia}
                alerta={alerta} aoConfirmar={(id, v) => movimentar('depositar', id, v)} />,
    saque: <Movimentacao tipo="saque" contaPadrao={sessao.idConta} eventosDaAgencia={eventosDaAgencia}
             alerta={alerta} aoConfirmar={(id, v) => movimentar('sacar', id, v)} />,
    transferencia: <Transferencia contaPadrao={sessao.idConta} agenciaEntrada={agenciaEntrada}
                     malha={malha} alerta={alerta} aoConfirmar={transferir} />,
    historico: <Historico contaPadrao={sessao.idConta} eventos={eventosDaConta} alerta={alerta}
                 aoBuscar={async (id) => {
                   limpar()
                   try { setEventosDaConta(await api.historico(agenciaEntrada, id, 30)) }
                   catch (erro) { tratar(erro); setEventosDaConta([]) }
                 }} />,
    extrato: <Extrato contaPadrao={sessao.idConta} extrato={extrato} alerta={alerta}
                aoConsolidar={async (contas) => {
                  limpar()
                  try { setExtrato(await api.extratoConsolidado(agenciaEntrada, contas)) }
                  catch (erro) { tratar(erro) }
                }} />,
    linhaDoTempo: <LinhaDoTempo eventos={eventosGlobais} aoGerarConcorrentes={async () => {
                    // Um evento local em cada agencia, quase ao mesmo tempo: sem troca de
                    // mensagem entre elas, os contadores avancam de forma independente e colidem.
                    await Promise.all(AGENCIAS.map((a) => {
                      const contaLocal = a.id
                      return api.depositar(a.id, contaLocal, 1).catch(() => null)
                    }))
                    const listas = await Promise.all(AGENCIAS.map((a) => api.eventos(a.id, 60).catch(() => [])))
                    setEventosGlobais(listas.flat())
                  }} />,
  }

  return (
    <Layout
      sessao={sessao}
      segundosRestantes={segundosRestantes}
      agenciaEntrada={agenciaEntrada}
      aoTrocarAgencia={(id) => { setAgenciaEntrada(id); limpar() }}
      relogioDaAgencia={malha[agenciaEntrada]?.relogioLamport}
      tela={tela}
      aoNavegar={(t) => { setTela(t); limpar() }}
      aoSair={sair}
      aoExpirarToken={expirarAgora}
    >
      {tela === 'painel' ? <div style={{ marginBottom: 'var(--space-4)' }}>
        {alerta ? <div className={`alerta ${alerta.tom === 'erro' ? 'erro' : ''}`}>
          <div><span className="alerta-titulo">{alerta.titulo}</span>
            {alerta.http ? <span className="alerta-http">HTTP {alerta.http}</span> : null}</div>
          <div className="alerta-texto">{alerta.texto}</div>
        </div> : null}
      </div> : null}
      {telas[tela]}
    </Layout>
  )
}
