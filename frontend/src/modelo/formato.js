/** MODEL — formatacao de apresentacao. */

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function dinheiro(valor) {
  if (valor === null || valor === undefined) return '—'
  return MOEDA.format(Number(valor))
}

/**
 * O carimbo vetorial como texto: [1, 0, 2].
 *
 * Um carimbo agora e uma LISTA, uma posicao por agencia — nao mais um inteiro.
 * E essa lista que permite dizer se dois eventos sao concorrentes.
 */
export function vetor(valores) {
  if (!Array.isArray(valores)) return '—'
  return `[${valores.join(', ')}]`
}

/** Hora de parede em 24h com milissegundos — a comparacao com o relogio logico. */
export function hora(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  const p = (n, casas = 2) => String(n).padStart(casas, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`
}

/** Rotulos humanos para o painel; nas tabelas tecnicas o tipo aparece cru. */
const ROTULOS = {
  CRIAR_CONTA: 'Abertura de conta',
  DEPOSITO: 'Depósito',
  SAQUE: 'Saque',
  TRANSFERENCIA_DEBITO: 'Transferência enviada',
  TRANSFERENCIA_CREDITO: 'Transferência recebida',
  TRANSFERENCIA_CREDITO_REMOTO: 'Recebida de outra agência',
  TRANSFERENCIA_FALHOU: 'Transferência falhou',
  CREDITO_REMOTO_FALHOU: 'Crédito remoto não aplicado',
  CREDITO_REMOTO_DUPLICADO: 'Reentrega ignorada',
}

export function rotuloDoEvento(tipo) {
  return ROTULOS[tipo] ?? tipo
}

const ENTRAM = ['DEPOSITO', 'TRANSFERENCIA_CREDITO', 'TRANSFERENCIA_CREDITO_REMOTO', 'CRIAR_CONTA']

/**
 * Eventos que NAO movem dinheiro: a transferencia que falhou, o credito remoto que
 * nao teve onde ser aplicado, a reentrega que foi ignorada. Sao os tres registros
 * que o Sprint 2 acrescentou ao log e nenhum deles altera saldo.
 */
const NAO_MOVEM = ['TRANSFERENCIA_FALHOU', 'CREDITO_REMOTO_FALHOU', 'CREDITO_REMOTO_DUPLICADO']

/**
 * Sinal do lancamento na visao da conta: entrada (+), saida (−) ou nenhum (·).
 *
 * O terceiro caso nao e enfeite. Com dois valores possiveis, "Reentrega ignorada"
 * aparecia no painel como "− 40,00" — a tela afirmando uma saida que nao existiu.
 */
export function sinalDoEvento(tipo) {
  if (NAO_MOVEM.includes(tipo)) return '·'
  return ENTRAM.includes(tipo) ? '+' : '−'
}

export function valorDoEvento(detalhes) {
  return detalhes?.valor ?? detalhes?.saldoInicial ?? null
}
