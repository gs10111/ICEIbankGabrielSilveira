import { useCallback, useEffect, useState } from 'react'
import { api, guardarSessao, lerSessao, limparSessao } from '../modelo/api.js'

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

  const expirarAgora = useCallback(() => {
    const invalida = { ...sessao, token: `${sessao.token}-invalidado`, expiraEm: Date.now() }
    guardarSessao(invalida)
    setSessao(invalida)
  }, [sessao])

  return { sessao, segundosRestantes, entrar, sair, expirarAgora }
}
