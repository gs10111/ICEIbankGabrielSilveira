export const PORTA_BASE = 4016

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

export function totalDaMalha() {
  return totalDeAgencias
}

export function agenciaResponsavel(idConta) {
  return ((idConta % totalDeAgencias) + totalDeAgencias) % totalDeAgencias
}

export function urlDaAgencia(idAgencia) {
  return AGENCIAS[idAgencia].url
}
