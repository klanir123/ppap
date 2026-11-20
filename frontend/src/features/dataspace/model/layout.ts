import type { Topology, TopologyPartner } from '../api'

/** Fixed drawing space; the SVG scales it to the available size (good for large screens). */
export const VIEW = { width: 1080, height: 700 } as const
export const SELF_SIZE = { w: 280, h: 108 } as const
export const PARTNER_SIZE = { w: 240, h: 88 } as const
export const CORE_SIZE = { w: 190, h: 64 } as const

export interface Point {
  x: number
  y: number
}

export type NodeGroup = 'self' | 'customer' | 'supplier' | 'other' | 'core'

export interface LayoutNode {
  id: string
  group: NodeGroup
  x: number
  y: number
  w: number
  h: number
  partner?: TopologyPartner
}

export interface LayoutEdge {
  /** Partner BPNL. */
  id: string
  /** Point on the border of the own connector. */
  self: Point
  /** Point on the border of the partner connector. */
  partner: Point
  /** Control point of the quadratic curve. */
  control: Point
  /** Label anchor (curve midpoint). */
  label: Point
  partnerRole?: 'CUSTOMER' | 'SUPPLIER'
}

export interface Layout {
  nodes: LayoutNode[]
  edges: LayoutEdge[]
  core: LayoutNode
}

const CENTER: Point = { x: VIEW.width / 2, y: VIEW.height / 2 }

function byName(a: TopologyPartner, b: TopologyPartner) {
  return a.name.localeCompare(b.name, 'de', { sensitivity: 'base' }) || a.bpnl.localeCompare(b.bpnl)
}

function relationRole(role: string | undefined | null): 'CUSTOMER' | 'SUPPLIER' | undefined {
  return role === 'CUSTOMER' || role === 'SUPPLIER' ? role : undefined
}

/** Group of a partner from the own point of view: customers above, suppliers below, others at the side. */
export function partnerGroup(p: TopologyPartner): NodeGroup {
  const role = p.relation?.partnerRole ?? (p.roles.length === 1 ? p.roles[0] : undefined)
  if (role === 'CUSTOMER') return 'customer'
  if (role === 'SUPPLIER') return 'supplier'
  return 'other'
}

/** Evenly spread x positions in [min, max] (single item centred). */
export function spread(count: number, min: number, max: number): number[] {
  if (count === 1) return [(min + max) / 2]
  const step = (max - min) / (count - 1)
  return Array.from({ length: count }, (_, i) => min + i * step)
}

/** Row of nodes on a gentle arc (outer nodes bend towards the centre line). */
function row(partners: TopologyPartner[], group: NodeGroup, baseY: number, bend: number): LayoutNode[] {
  const maxHalf = CENTER.x - PARTNER_SIZE.w / 2 - 10
  const half = Math.min(maxHalf, 120 + partners.length * 110)
  const xs = spread(partners.length, CENTER.x - half, CENTER.x + half)
  return partners.map((partner, i) => {
    const dx = Math.abs(xs[i]! - CENTER.x) / maxHalf
    return {
      id: partner.bpnl,
      group,
      partner,
      x: xs[i]!,
      y: baseY + bend * dx * dx,
      w: PARTNER_SIZE.w,
      h: PARTNER_SIZE.h,
    }
  })
}

/** Point where the line from a rect's centre towards `toward` leaves the rect (plus a small gap). */
export function rectEdgePoint(node: Pick<LayoutNode, 'x' | 'y' | 'w' | 'h'>, toward: Point, gap = 6): Point {
  const dx = toward.x - node.x
  const dy = toward.y - node.y
  if (dx === 0 && dy === 0) return { x: node.x, y: node.y }
  const sx = dx === 0 ? Infinity : (node.w / 2 + gap) / Math.abs(dx)
  const sy = dy === 0 ? Infinity : (node.h / 2 + gap) / Math.abs(dy)
  const s = Math.min(sx, sy)
  return { x: node.x + dx * s, y: node.y + dy * s }
}

export function quadraticPoint(p0: Point, c: Point, p1: Point, t: number): Point {
  const u = 1 - t
  return { x: u * u * p0.x + 2 * u * t * c.x + t * t * p1.x, y: u * u * p0.y + 2 * u * t * c.y + t * t * p1.y }
}

/**
 * Deterministic layout from the topology: own connector in the centre, customers on an arc above,
 * suppliers below, partners without relation on the right; core services (identity) on the left.
 * Same input → same coordinates (partners sorted by name, then BPNL).
 */
export function layoutTopology(topology: Pick<Topology, 'self' | 'partners'>): Layout {
  const sorted = [...topology.partners].sort(byName)
  const customers = sorted.filter((p) => partnerGroup(p) === 'customer')
  const suppliers = sorted.filter((p) => partnerGroup(p) === 'supplier')
  const others = sorted.filter((p) => partnerGroup(p) === 'other')

  const self: LayoutNode = {
    id: topology.self.bpnl,
    group: 'self',
    x: CENTER.x,
    y: CENTER.y,
    ...{ w: SELF_SIZE.w, h: SELF_SIZE.h },
  }
  const nodes: LayoutNode[] = [
    self,
    ...row(customers, 'customer', 64, 36),
    ...row(suppliers, 'supplier', VIEW.height - 64, -36),
    ...spread(others.length || 1, 200, VIEW.height - 200)
      .slice(0, others.length)
      .map((y, i) => ({
        id: others[i]!.bpnl,
        group: 'other' as const,
        partner: others[i]!,
        x: VIEW.width - PARTNER_SIZE.w / 2 - 16,
        y,
        w: PARTNER_SIZE.w,
        h: PARTNER_SIZE.h,
      })),
  ]
  const core: LayoutNode = {
    id: 'core',
    group: 'core',
    x: CORE_SIZE.w / 2 + 16,
    y: CENTER.y,
    w: CORE_SIZE.w,
    h: CORE_SIZE.h,
  }

  const edges = nodes
    .filter((n) => n.partner)
    .map((n): LayoutEdge => {
      const a = rectEdgePoint(self, n)
      const b = rectEdgePoint(n, self)
      // gentle curve: control point pushed sideways (perpendicular), direction depends on the side
      const mx = (a.x + b.x) / 2
      const my = (a.y + b.y) / 2
      const len = Math.hypot(b.x - a.x, b.y - a.y) || 1
      const side = n.x < CENTER.x ? -1 : n.x > CENTER.x ? 1 : n.group === 'customer' ? 1 : -1
      const bendBy = Math.min(60, len * 0.18) * side
      const control = { x: mx + (-(b.y - a.y) / len) * bendBy, y: my + ((b.x - a.x) / len) * bendBy }
      return {
        id: n.id,
        self: a,
        partner: b,
        control,
        label: quadraticPoint(a, control, b, 0.5),
        partnerRole: relationRole(n.partner?.relation?.partnerRole),
      }
    })

  return { nodes, edges, core }
}

/** SVG path (`M … Q …`) of an edge in the given direction. */
export function edgePath(edge: LayoutEdge, from: 'self' | 'partner' = 'self'): string {
  const [a, b] = from === 'self' ? [edge.self, edge.partner] : [edge.partner, edge.self]
  return `M${a.x.toFixed(1)},${a.y.toFixed(1)} Q${edge.control.x.toFixed(1)},${edge.control.y.toFixed(1)} ${b.x.toFixed(1)},${b.y.toFixed(1)}`
}

/** Endpoints in travel direction (`toPartner`: own connector → partner). */
export function travel(edge: LayoutEdge, a: { direction: 'toPartner' | 'toSelf' }): [Point, Point] {
  return a.direction === 'toPartner' ? [edge.self, edge.partner] : [edge.partner, edge.self]
}
