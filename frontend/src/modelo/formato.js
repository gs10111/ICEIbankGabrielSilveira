/** MODEL — formatacao de apresentacao. */

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function dinheiro(valor) {
  if (valor === null || valor === undefined) return '—'
  return MOEDA.format(Number(valor))
}

/** Hora de parede em 24h com milissegundos — a comparacao com Lamport na Parte E. */
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
}

export function rotuloDoEvento(tipo) {
  return ROTULOS[tipo] ?? tipo
}

/** Sinal do lancamento na visao da conta: credito (+) ou debito (−). */
export function sinalDoEvento(tipo) {
  return ['DEPOSITO', 'TRANSFERENCIA_CREDITO', 'TRANSFERENCIA_CREDITO_REMOTO', 'CRIAR_CONTA'].includes(tipo)
    ? '+'
    : '−'
}

export function valorDoEvento(detalhes) {
  return detalhes?.valor ?? detalhes?.saldoInicial ?? null
}
