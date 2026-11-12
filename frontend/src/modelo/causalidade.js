export function compararVetores(primeiro, segundo) {
  let primeiroNuncaMaior = true
  let segundoNuncaMaior = true

  for (let i = 0; i < primeiro.length; i++) {
    if (primeiro[i] > segundo[i]) primeiroNuncaMaior = false
    if (segundo[i] > primeiro[i]) segundoNuncaMaior = false
  }

  if (primeiroNuncaMaior && segundoNuncaMaior) return 'IGUAIS'
  if (primeiroNuncaMaior) return 'ANTES'
  if (segundoNuncaMaior) return 'DEPOIS'
  return 'CONCORRENTES'
}

export function paresConcorrentes(eventos) {
  const pares = []

  for (let i = 0; i < eventos.length; i++) {
    for (let j = i + 1; j < eventos.length; j++) {
      const primeiro = eventos[i]
      const segundo = eventos[j]
      if (primeiro.agencia === segundo.agencia) continue
      if (!comparaveis(primeiro.timestampVetorial, segundo.timestampVetorial)) continue
      if (compararVetores(primeiro.timestampVetorial, segundo.timestampVetorial) !== 'CONCORRENTES') continue
      pares.push({ primeiro, segundo })
    }
  }

  return pares
}

function comparaveis(umVetor, outroVetor) {
  return Array.isArray(umVetor) && Array.isArray(outroVetor)
    && umVetor.length > 0 && umVetor.length === outroVetor.length
}
