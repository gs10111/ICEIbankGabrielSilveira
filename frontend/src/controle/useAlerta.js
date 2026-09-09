import { useCallback, useState } from 'react'
import { ErroDaApi } from '../modelo/api.js'

/**
 * CONTROLLER — traduz erro HTTP em mensagem de TELA.
 *
 * Requisito 5 da Parte G: o erro tem que aparecer para quem usa, nao so no console.
 */
export function useAlerta() {
  const [alerta, setAlerta] = useState(null)

  const limpar = useCallback(() => setAlerta(null), [])

  const sucesso = useCallback((titulo, texto) => setAlerta({ tom: 'ok', titulo, texto }), [])

  const doErro = useCallback((erro) => {
    if (erro instanceof ErroDaApi) {
      setAlerta({ tom: 'erro', http: erro.http, titulo: tituloPara(erro.http), texto: erro.message })
      return
    }
    setAlerta({ tom: 'erro', titulo: 'Erro inesperado', texto: String(erro?.message ?? erro) })
  }, [])

  return { alerta, sucesso, doErro, limpar }
}

function tituloPara(http) {
  switch (http) {
    case 400: return 'Requisição inválida'
    case 401: return 'Sessão expirada'
    case 404: return 'Não encontrado'
    case 409: return 'Conflito'
    case 502: return 'Falha entre agências'
    case 503: return 'Agência fora do ar'
    default: return 'Erro'
  }
}
