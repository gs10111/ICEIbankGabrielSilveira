import { urlDaAgencia } from './agencias.js'

/**
 * MODEL — acesso a API das agencias.
 *
 * O token fica em localStorage e e reenviado por ESTE modulo em toda requisicao:
 * nenhuma tela monta header de autenticacao. E o equivalente a um interceptor —
 * um unico ponto sabe da existencia do JWT (resposta da pergunta 12.3.1).
 */
const CHAVE_TOKEN = 'iceibank.token'
const CHAVE_SESSAO = 'iceibank.sessao'

export function guardarSessao(sessao) {
  localStorage.setItem(CHAVE_TOKEN, sessao.token)
  localStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao))
}

export function lerSessao() {
  try {
    const bruto = localStorage.getItem(CHAVE_SESSAO)
    return bruto ? JSON.parse(bruto) : null
  } catch {
    return null
  }
}

export function limparSessao() {
  localStorage.removeItem(CHAVE_TOKEN)
  localStorage.removeItem(CHAVE_SESSAO)
}

export function tokenAtual() {
  return localStorage.getItem(CHAVE_TOKEN)
}

/** Erro de API com o status HTTP preservado — a View precisa dele para a faixa de alerta. */
export class ErroDaApi extends Error {
  constructor(http, mensagem) {
    super(mensagem)
    this.http = http
  }
}

async function requisitar(idAgencia, caminho, opcoes = {}) {
  const cabecalhos = { 'Content-Type': 'application/json', ...(opcoes.headers ?? {}) }
  const token = tokenAtual()
  if (token) cabecalhos.Authorization = `Bearer ${token}`

  let resposta
  try {
    resposta = await fetch(`${urlDaAgencia(idAgencia)}${caminho}`, { ...opcoes, headers: cabecalhos })
  } catch {
    // fetch so rejeita por rede: agencia derrubada, DNS, CORS.
    throw new ErroDaApi(503, `Agência ${idAgencia} não respondeu (fora do ar?)`)
  }

  const texto = await resposta.text()
  const corpo = texto ? JSON.parse(texto) : null

  if (!resposta.ok) {
    throw new ErroDaApi(resposta.status, corpo?.erro ?? `Erro ${resposta.status}`)
  }
  return corpo
}

export const api = {
  login: (idAgencia, idConta, senha) =>
    requisitar(idAgencia, '/auth/login', { method: 'POST', body: JSON.stringify({ idConta, senha }) }),

  status: (idAgencia) => requisitar(idAgencia, '/status'),

  consultarConta: (idAgencia, idConta) => requisitar(idAgencia, `/contas/${idConta}`),

  depositar: (idAgencia, idConta, valor) =>
    requisitar(idAgencia, `/contas/${idConta}/depositar`, { method: 'POST', body: JSON.stringify({ valor }) }),

  sacar: (idAgencia, idConta, valor) =>
    requisitar(idAgencia, `/contas/${idConta}/sacar`, { method: 'POST', body: JSON.stringify({ valor }) }),

  transferir: (idAgencia, ordem, chaveIdempotencia) =>
    requisitar(idAgencia, '/transferencias', {
      method: 'POST',
      headers: chaveIdempotencia ? { 'Idempotency-Key': chaveIdempotencia } : {},
      body: JSON.stringify(ordem),
    }),

  historico: (idAgencia, idConta, limite = 30) =>
    requisitar(idAgencia, `/contas/${idConta}/historico?limite=${limite}`),

  eventos: (idAgencia, limite = 50) => requisitar(idAgencia, `/eventos?limite=${limite}`),

  extratoConsolidado: (idAgencia, contas) =>
    requisitar(idAgencia, `/extrato-consolidado?contas=${contas.join(',')}`),
}
