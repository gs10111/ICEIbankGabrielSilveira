/**
 * Teste da descoberta de topologia. Roda com `npm test` — usa o runner embutido
 * do Node (node:test), sem dependencia nova.
 *
 * O que ele trava: o frontend NAO pode voltar a repetir o numero de agencias.
 * Se alguem reintroduzir um 3 literal aqui, o caso "backend informa 5" quebra.
 */
import { test, describe, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'

const MODULO = new URL('./agencias.js', import.meta.url).href
let contador = 0

/** Importa o modulo do zero, com o fetch trocado por este aqui. */
async function comBackendRespondendo(resposta) {
  globalThis.fetch = resposta
  return import(`${MODULO}?caso=${contador++}`)
}

const fetchOriginal = globalThis.fetch
afterEach(() => { globalThis.fetch = fetchOriginal })

describe('descobrirMalha', () => {
  test('backend informa 5 agencias: a malha e o roteamento acompanham', async () => {
    const malha = await comBackendRespondendo(async () => ({ ok: true, json: async () => ({ totalDeAgencias: 5 }) }))

    const r = await malha.descobrirMalha()

    assert.deepEqual(r, { descoberta: true, total: 5 })
    assert.equal(malha.AGENCIAS.length, 5)
    assert.deepEqual(malha.AGENCIAS.map((a) => a.porta), [4016, 4017, 4018, 4019, 4020])
    assert.equal(malha.agenciaResponsavel(7), 2, '7 % 5')
    assert.equal(malha.agenciaResponsavel(3), 3, 'com N=3 daria 0; com N=5 da 3')
  })

  test('agencia de entrada fora do ar: mantem o padrao e nao derruba o app', async () => {
    const malha = await comBackendRespondendo(async () => { throw new Error('fetch failed') })

    const r = await malha.descobrirMalha()

    assert.equal(r.descoberta, false)
    assert.ok(r.motivo.length > 0, 'precisa dizer por que nao descobriu')
    assert.equal(malha.AGENCIAS.length, 3)
    assert.equal(malha.agenciaResponsavel(4), 1, 'roteamento continua funcionando')
  })

  test('totalDeAgencias invalido: malha intacta', async () => {
    const malha = await comBackendRespondendo(async () => ({ ok: true, json: async () => ({ totalDeAgencias: 'tres' }) }))

    const r = await malha.descobrirMalha()

    assert.equal(r.descoberta, false)
    assert.equal(malha.AGENCIAS.length, 3)
  })

  test('/status responde 503: o codigo aparece no motivo', async () => {
    const malha = await comBackendRespondendo(async () => ({ ok: false, status: 503, json: async () => ({}) }))

    assert.deepEqual(await malha.descobrirMalha(), { descoberta: false, total: 3, motivo: 'HTTP 503' })
  })
})

describe('agenciaResponsavel', () => {
  test('a mesma regra do Particionador.java, inclusive para id negativo', async () => {
    const malha = await comBackendRespondendo(fetchOriginal)

    assert.deepEqual([0, 1, 2, 3, 4, 5].map(malha.agenciaResponsavel), [0, 1, 2, 0, 1, 2])
    assert.equal(malha.agenciaResponsavel(-1), 2, 'JS da -1 para -1 % 3; o ajuste corrige')
  })
})
