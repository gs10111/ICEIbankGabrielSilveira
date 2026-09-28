/**
 * PARTE D no frontend — a mesma analise causal que o `--mesclar-logs` faz no backend.
 *
 * Ela mora em `modelo/` e nao dentro do JSX de proposito: assim ela e uma funcao
 * pura, testavel com o runner do Node, e a tela so exibe o que ela devolve. Fosse
 * um laco dentro do componente, so um teste de renderizacao alcancaria — e o
 * projeto nao tem (nem precisa de) um.
 */
import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { compararVetores, paresConcorrentes } from './causalidade.js'

describe('compararVetores', () => {
  test('[3,1,0] x [3,2,0]: o primeiro nunca e maior, entao aconteceu ANTES', () => {
    assert.equal(compararVetores([3, 1, 0], [3, 2, 0]), 'ANTES')
  })

  test('[3,1,0] x [1,3,0]: cada um sabe de algo que o outro nao sabe — CONCORRENTES', () => {
    assert.equal(compararVetores([3, 1, 0], [1, 3, 0]), 'CONCORRENTES')
  })

  test('vetores identicos sao o mesmo ponto no tempo logico', () => {
    assert.equal(compararVetores([2, 2, 0], [2, 2, 0]), 'IGUAIS')
  })
})

describe('paresConcorrentes', () => {
  test('duas aberturas independentes, uma em cada agencia, formam um par concorrente', () => {
    const pares = paresConcorrentes([
      { agencia: 'agencia-0', tipo: 'CRIAR_CONTA', timestampVetorial: [1, 0, 0] },
      { agencia: 'agencia-1', tipo: 'CRIAR_CONTA', timestampVetorial: [0, 1, 0] },
    ])

    assert.equal(pares.length, 1)
    assert.equal(pares[0].primeiro.agencia, 'agencia-0')
    assert.equal(pares[0].segundo.agencia, 'agencia-1')
  })

  test('transferencia entre agencias NAO e par concorrente: a regra 3 absorveu o vetor da origem', () => {
    const pares = paresConcorrentes([
      { agencia: 'agencia-0', tipo: 'TRANSFERENCIA_DEBITO', timestampVetorial: [1, 0, 0] },
      { agencia: 'agencia-1', tipo: 'TRANSFERENCIA_CREDITO_REMOTO', timestampVetorial: [1, 1, 0] },
    ])

    assert.deepEqual(pares, [])
  })

  test('dois eventos da MESMA agencia nunca sao concorrentes: o relogio dela e um so', () => {
    const pares = paresConcorrentes([
      { agencia: 'agencia-0', tipo: 'DEPOSITO', timestampVetorial: [1, 0, 0] },
      { agencia: 'agencia-0', tipo: 'SAQUE', timestampVetorial: [2, 0, 0] },
    ])

    assert.deepEqual(pares, [])
  })

  test('evento sem vetor nao vira par: "nao sei" e diferente de "sao independentes"', () => {
    const pares = paresConcorrentes([
      { agencia: 'agencia-0', tipo: 'CRIAR_CONTA', timestampVetorial: [1, 0, 0] },
      { agencia: 'agencia-1', tipo: 'CRIAR_CONTA' },
    ])

    assert.deepEqual(pares, [])
  })
})
