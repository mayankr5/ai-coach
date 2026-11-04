import { StyleSheet, Text, View } from 'react-native'
import type { Schemas } from '../../api/client'
import { formatDay, today, toIsoDate } from '../../dates'
import { colors, radius, space } from '../../theme'
import { Card } from './Card'

export function Milestones({ milestones }: { milestones: Schemas['MilestonePlan'][] }) {
  const now = toIsoDate(today())
  const current = milestones.find(m => m.targetDate >= now)?.id
  return (
    <Card title="Milestones">
      {milestones.map(m => {
        const done = m.tasks.filter(t => t.status === 'DONE').length
        const isCurrent = m.id === current
        return (
          <View key={m.id} accessibilityLabel={isCurrent ? `Current milestone: ${m.title}` : undefined}
            style={[styles.milestone, isCurrent && styles.current]}>
            <Text style={styles.title}>{m.title}</Text>
            <Text style={styles.small}>
              By {formatDay(m.targetDate)}
              {m.tasks.length > 0 ? ` · ${done} of ${m.tasks.length} tasks done` : ' · tasks planned later'}
            </Text>
          </View>
        )
      })}
    </Card>
  )
}

const styles = StyleSheet.create({
  milestone: { paddingVertical: space.sm, paddingHorizontal: space.md, gap: 2, borderRadius: radius.md },
  current: { backgroundColor: '#eef2ff', borderWidth: 1, borderColor: '#c7d2fe' },
  title: { fontSize: 14, fontWeight: '600', color: colors.text },
  small: { fontSize: 12, color: colors.textMuted },
})
