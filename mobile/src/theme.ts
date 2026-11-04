/** The web app's palette (Tailwind indigo, slate, amber, emerald, red), so both apps feel the same. */
export const colors = {
  primary: '#4f46e5',
  primaryPressed: '#4338ca',
  onPrimary: '#ffffff',
  background: '#f8fafc',
  surface: '#ffffff',
  border: '#cbd5e1',
  divider: '#e2e8f0',
  text: '#0f172a',
  textMuted: '#64748b',
  textFaint: '#94a3b8',
  danger: '#b91c1c',
  dangerBackground: '#fef2f2',
  warning: '#b45309',
  success: '#047857',
} as const

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const

export const radius = { md: 8, lg: 12, xl: 16 } as const
