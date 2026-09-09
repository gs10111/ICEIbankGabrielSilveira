/**
 * MODEL — configuracao da malha e a regra de particao.
 *
 * O OFFSET pessoal (16, dois ultimos digitos do RA 1466316) desloca as portas:
 * 4000 + 16 = 4016. Cada agencia responde apenas pelas contas em que
 * `id % 3 === idDaAgencia`.
 */
export const TOTAL_DE_AGENCIAS = 3
export const PORTA_BASE = 4016

export const AGENCIAS = [0, 1, 2].map((id) => ({
  id,
  porta: PORTA_BASE + id,
  url: `http://localhost:${PORTA_BASE + id}`,
  rotulo: `AG ${id}`,
}))

/** A MESMA regra do Particionador.java do backend. Aqui ela evita uma ida ao servidor. */
export function agenciaResponsavel(idConta) {
  return ((idConta % TOTAL_DE_AGENCIAS) + TOTAL_DE_AGENCIAS) % TOTAL_DE_AGENCIAS
}

export function urlDaAgencia(idAgencia) {
  return AGENCIAS[idAgencia].url
}
