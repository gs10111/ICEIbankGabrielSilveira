/** VIEW — componentes de apresentacao do design system Industry. */

/** Toda moldura tecnica leva as quatro marcas de registro. Nunca omitir. */
export function Blueprint({ className = '', style, children }) {
  return (
    <div className={`blueprint ${className}`} style={style}>
      <i className="corner tl" /><i className="corner tr" />
      <i className="corner bl" /><i className="corner br" />
      {children}
    </div>
  )
}

export function Alerta({ alerta }) {
  if (!alerta) return null
  return (
    <div className={`alerta ${alerta.tom === 'erro' ? 'erro' : ''}`}>
      <div>
        <span className="alerta-titulo">{alerta.titulo}</span>
        {alerta.http ? <span className="alerta-http">HTTP {alerta.http}</span> : null}
      </div>
      <div className="alerta-texto">{alerta.texto}</div>
    </div>
  )
}

export function Campo({ rotulo, ...props }) {
  return (
    <label className="field">
      <span style={{ display: 'block', fontSize: 11, fontWeight: 600, textTransform: 'uppercase',
        letterSpacing: '0.12em', color: 'var(--color-neutral-700)', marginBottom: 'var(--space-2)' }}>
        {rotulo}
      </span>
      <input className="input" {...props} />
    </label>
  )
}

export function Segmentado({ nome, valor, aoMudar, opcoes }) {
  return (
    <div className="seg">
      {opcoes.map((opcao) => (
        <label className="seg-opt" key={opcao.valor}>
          <input
            type="radio"
            name={nome}
            checked={valor === opcao.valor}
            onChange={() => aoMudar(opcao.valor)}
          />
          <span>{opcao.rotulo}</span>
        </label>
      ))}
    </div>
  )
}

export function Tag({ tipo = 'neutral', children }) {
  return <span className={`tag tag-${tipo}`}>{children}</span>
}

/* Icones Lucide inline, stroke-width 1.5, em accent. */
const svg = { width: 20, height: 20, viewBox: '0 0 24 24', fill: 'none',
  stroke: 'var(--color-accent)', strokeWidth: 1.5, strokeLinecap: 'round', strokeLinejoin: 'round' }

export const IconeDeposito = () => (
  <svg {...svg}><path d="M12 17V3" /><path d="m6 11 6 6 6-6" /><path d="M19 21H5" /></svg>
)
export const IconeSaque = () => (
  <svg {...svg}><path d="M12 3v14" /><path d="m18 9-6-6-6 6" /><path d="M19 21H5" /></svg>
)
export const IconeTransferencia = () => (
  <svg {...svg}><path d="M8 3 4 7l4 4" /><path d="M4 7h16" /><path d="m16 21 4-4-4-4" /><path d="M20 17H4" /></svg>
)
