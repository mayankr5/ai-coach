import { StyleSheet, Text, View } from 'react-native'
import { messageOf } from '../../api/client'
import { useProgress } from '../../api/plan'
import { formatDay } from '../../dates'
import { colors, radius, space } from '../../theme'
import { ErrorMessage, Spinner } from '../ui'
import { Card } from './Card'

export function ProgressCard({ goalId }: { goalId: number }) {
  const progress = useProgress(goalId)
  if (progress.isPending) return <Card title="Progress"><Spinner /></Card>
  if (!progress.data) return <Card title="Progress"><ErrorMessage>{messageOf(progress.error)}</ErrorMessage></Card>

  const p = progress.data
  const healthy = p.minutesBehind === 0
  const tone = healthy ? colors.success : colors.warning
  return (
    <Card title="Progress">
      <View style={styles.row}>
        <Text style={[styles.status, { color: tone }]}>{healthy ? 'On track' : `${p.minutesBehind} min behind`}</Text>
        <Text style={styles.small}>{p.healthPercent}%</Text>
      </View>
      <View accessibilityRole="progressbar" accessibilityLabel="Plan health"
        accessibilityValue={{ min: 0, max: 100, now: p.healthPercent }} style={styles.track}>
        <View style={[styles.bar, { width: `${p.healthPercent}%`, backgroundColor: healthy ? colors.done : colors.partial }]} />
      </View>
      {p.nextMilestone && (
        <Text style={styles.small}>
          {healthy ? 'Enough time' : 'Not enough time at your normal pace'} for “{p.nextMilestone.title}” by{' '}
          {formatDay(p.nextMilestone.targetDate)}
        </Text>
      )}
      <View style={styles.stats}>
        <Stat label="Tasks done" value={`${p.doneTasks} of ${p.totalTasks}`} />
        <Stat label="Completion" value={p.completionRate === null ? '–' : `${Math.round(p.completionRate * 100)}%`} />
        <Stat label="Partly done" value={String(p.partialTasks)} />
        <Stat label="Skipped" value={String(p.skippedTasks)} />
      </View>
    </Card>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.small}>{label}</Text>
      <Text style={styles.statValue}>{value}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  status: { fontSize: 15, fontWeight: '600' },
  small: { fontSize: 13, color: colors.textMuted },
  track: { height: 8, borderRadius: 4, backgroundColor: colors.divider, overflow: 'hidden' },
  bar: { height: 8, borderRadius: 4 },
  stats: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm },
  stat: {
    flexBasis: '48%', flexGrow: 1, paddingVertical: space.sm, paddingHorizontal: space.md, borderRadius: radius.md,
    backgroundColor: colors.background,
  },
  statValue: { fontSize: 16, fontWeight: '600', color: colors.text },
})
