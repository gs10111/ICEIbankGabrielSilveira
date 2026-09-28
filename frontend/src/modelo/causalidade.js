/**
 * MODEL — PARTE D: a analise causal dos carimbos vetoriais.
 *
 * E a mesma regra do backend (`CarimboVetorial.comparar`), reescrita aqui porque o
 * frontend nao fala Java. Nao ha "duplicacao a eliminar": sao dois processos
 * separados, cada um com sua linguagem, e nenhum pode chamar o codigo do outro sem
 * inventar um acoplamento que o sprint inteiro existe para remover.
 */

/**
 * A relacao causal entre dois vetores, posicao a posicao.
 *
 * Se v1[i] <= v2[i] em TODA posicao, o primeiro aconteceu-antes: nao ha como o
 * segundo ter contribuido para ele. Se nenhum domina o outro, os dois sao
 * CONCORRENTES — cada um sabe de algo que o outro nao sabe.
 *
 * Quatro respostas, nao duas. Um comparador de ordenacao devolveria -1/0/1 e seria
 * obrigado a mentir num dos casos: ordem parcial nao cabe em ordem total.
 */
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

/**
 * Os pares comprovadamente concorrentes, entre agencias DIFERENTES.
 *
 * Pares da mesma agencia sao pulados: dentro de uma agencia existe um relogio so e
 * ele sempre incrementa, entao dois eventos dela jamais sao concorrentes.
 *
 * O(n^2) no numero de eventos, igual ao script do roteiro. A tela recebe os ultimos
 * eventos, nao o log inteiro, entao o n aqui e pequeno de proposito.
 */
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

/**
 * Evento sem vetor (log truncado) ou de malha com outro numero de agencias nao da
 * para comparar. Afirmar concorrencia sem vetor seria inventar: "nao sei" e
 * diferente de "sao independentes".
 */
function comparaveis(umVetor, outroVetor) {
  return Array.isArray(umVetor) && Array.isArray(outroVetor)
    && umVetor.length > 0 && umVetor.length === outroVetor.length
}
