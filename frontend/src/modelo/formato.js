const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function dinheiro(valor) {
  if (valor === null || valor === undefined) return '—'
  return MOEDA.format(Number(valor))
}

export function vetor(valores) {
  if (!Array.isArray(valores)) return '—'
  return `[${valores.join(', ')}]`
}

export function hora(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  const p = (n, casas = 2) => String(n).padStart(casas, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`
}

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

const NAO_MOVEM = ['TRANSFERENCIA_FALHOU', 'CREDITO_REMOTO_FALHOU', 'CREDITO_REMOTO_DUPLICADO']

export function sinalDoEvento(tipo) {
  if (NAO_MOVEM.includes(tipo)) return '·'
  return ENTRAM.includes(tipo) ? '+' : '−'
}

export function valorDoEvento(detalhes) {
  return detalhes?.valor ?? detalhes?.saldoInicial ?? null
}
