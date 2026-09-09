import { useCallback, useEffect, useState } from 'react'
import { api, guardarSessao, lerSessao, limparSessao } from '../modelo/api.js'

/**
 * CONTROLLER — sessao, token e contagem regressiva.
 *
 * Pergunta 12.3.2 do roteiro: quando o token expira no meio do uso, a interface
 * AVISA. Aqui isso acontece de duas formas — o contador zera e dispara `aoExpirar`,
 * e qualquer 401 vindo da API derruba a sessao com mensagem visivel.
 */
export function useAutenticacao() {
  const [sessao, setSessao] = useState(() => lerSessao())
  const [segundosRestantes, setSegundosRestantes] = useState(0)

  useEffect(() => {
    if (!sessao) return undefined
    const fim = sessao.expiraEm
    const tique = () => setSegundosRestantes(Math.max(0, Math.round((fim - Date.now()) / 1000)))
    tique()
    const intervalo = setInterval(tique, 1000)
    return () => clearInterval(intervalo)
  }, [sessao])

  const entrar = useCallback(async (idAgencia, idConta, senha) => {
    const resposta = await api.login(idAgencia, idConta, senha)
    const nova = {
      token: resposta.token,
      idConta: resposta.idConta,
      nomeAluno: resposta.nomeAluno,
      agencia: resposta.agencia,
      expiraEm: Date.now() + resposta.expiraEmSegundos * 1000,
    }
    guardarSessao(nova)
    setSessao(nova)
    return nova
  }, [])

  const sair = useCallback(() => {
    limparSessao()
    setSessao(null)
  }, [])

  /** Invalida o token na hora — usado para o print auth-token-expirado.png. */
  const expirarAgora = useCallback(() => {
    const invalida = { ...sessao, token: `${sessao.token}-invalidado`, expiraEm: Date.now() }
    guardarSessao(invalida)
    setSessao(invalida)
  }, [sessao])

  return { sessao, segundosRestantes, entrar, sair, expirarAgora }
}
