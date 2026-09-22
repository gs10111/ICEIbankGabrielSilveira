/**
 * MODEL — a malha de agencias, DESCOBERTA do backend.
 *
 * O OFFSET pessoal (16, dois ultimos digitos do RA 1466316) desloca as portas:
 * 4000 + 16 = 4016.
 *
 * Quem manda no numero de agencias e o backend (AGENCIA_TOTAL / Particionador).
 * Antes este arquivo repetia o 3 em JavaScript, entao trocar AGENCIA_TOTAL
 * quebrava o frontend em silencio: ele continuaria roteando por `id % 3` contra
 * um backend particionado de outro jeito. Agora o numero vem de GET /status.
 *
 * A REGRA (`id % N`) continua aqui de proposito: ela precisa decidir a qual
 * agencia falar ANTES da requisicao — perguntar ao servidor a qual servidor
 * perguntar seria uma volta a mais em toda operacao. O que era duplicado e o
 * N, nao a formula.
 */

/**
 * Ponto de partida fixo: nao da para perguntar a topologia sem saber a quem.
 * E o unico endereco que o frontend precisa saber de cor.
 */
export const PORTA_BASE = 4016

/**
 * O host onde as agencias respondem.
 *
 * Nao e `localhost` fixo: em container (ou em qualquer maquina que nao seja a sua),
 * o browser que abre esta pagina precisa falar com o MESMO host de onde ela veio.
 * `window.location.hostname` resolve os dois casos sem configuracao. O fallback
 * existe so para rodar os testes fora do browser.
 */
const HOST = (typeof window !== 'undefined' && window.location?.hostname) || 'localhost' 

let totalDeAgencias = 3
export let AGENCIAS = montarMalha(totalDeAgencias)

function montarMalha(total) {
  return Array.from({ length: total }, (_, id) => ({
    id,
    porta: PORTA_BASE + id,
    url: `http://${HOST}:${PORTA_BASE + id}`,
    rotulo: `AG ${id}`,
  }))
}

/**
 * Le `totalDeAgencias` do /status da agencia de entrada e remonta a malha.
 *
 * Chamado UMA vez, antes do primeiro render (ver main.jsx): assim `AGENCIAS`
 * segue sendo leitura sincrona nas telas — o live binding do ES module propaga
 * a reatribuicao para quem importou.
 *
 * /status e publico, entao isto funciona antes do login. Se a agencia de entrada
 * estiver fora do ar, mantem o padrao e devolve o motivo: a aplicacao sobe do
 * mesmo jeito e a "malha de agencias" do Painel mostra quem nao respondeu.
 */
export async function descobrirMalha() {
  try {
    const resposta = await fetch(`http://${HOST}:${PORTA_BASE}/status`, {
      signal: AbortSignal.timeout(3000),
    })
    if (!resposta.ok) {
      return { descoberta: false, total: totalDeAgencias, motivo: `HTTP ${resposta.status}` }
    }
    const status = await resposta.json()
    const total = Number(status.totalDeAgencias)
    if (!Number.isInteger(total) || total < 1) {
      return { descoberta: false, total: totalDeAgencias, motivo: `totalDeAgencias invalido: ${status.totalDeAgencias}` }
    }
    totalDeAgencias = total
    AGENCIAS = montarMalha(total)
    return { descoberta: true, total }
  } catch (erro) {
    return { descoberta: false, total: totalDeAgencias, motivo: String(erro?.message ?? erro) }
  }
}

/** Quantas agencias o backend informou. */
export function totalDaMalha() {
  return totalDeAgencias
}

/** A MESMA regra do Particionador.java — agora com o N que o backend informou. */
export function agenciaResponsavel(idConta) {
  return ((idConta % totalDeAgencias) + totalDeAgencias) % totalDeAgencias
}

export function urlDaAgencia(idAgencia) {
  return AGENCIAS[idAgencia].url
}
