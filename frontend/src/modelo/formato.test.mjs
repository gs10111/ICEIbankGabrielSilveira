import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { rotuloDoEvento, sinalDoEvento } from './formato.js'

describe('sinalDoEvento', () => {
  test('deposito e credito entram com +', () => {
    assert.equal(sinalDoEvento('DEPOSITO'), '+')
    assert.equal(sinalDoEvento('TRANSFERENCIA_CREDITO_REMOTO'), '+')
  })

  test('saque e debito saem com −', () => {
    assert.equal(sinalDoEvento('SAQUE'), '−')
    assert.equal(sinalDoEvento('TRANSFERENCIA_DEBITO'), '−')
  })

  test('evento que nao move dinheiro nao leva sinal de valor', () => {
    assert.equal(sinalDoEvento('CREDITO_REMOTO_DUPLICADO'), '·')
    assert.equal(sinalDoEvento('CREDITO_REMOTO_FALHOU'), '·')
    assert.equal(sinalDoEvento('TRANSFERENCIA_FALHOU'), '·')
  })
})

describe('rotuloDoEvento', () => {
  test('os eventos do Sprint 2 tem nome em portugues, nao a constante crua', () => {
    assert.equal(rotuloDoEvento('CREDITO_REMOTO_DUPLICADO'), 'Reentrega ignorada')
    assert.equal(rotuloDoEvento('CREDITO_REMOTO_FALHOU'), 'Crédito remoto não aplicado')
  })

  test('tipo desconhecido aparece cru, nunca em branco', () => {
    assert.equal(rotuloDoEvento('ALGO_NOVO'), 'ALGO_NOVO')
  })
})
