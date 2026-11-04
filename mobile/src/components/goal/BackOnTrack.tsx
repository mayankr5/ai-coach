import { router } from 'expo-router'
import { Alert, StyleSheet, Text, View } from 'react-native'
import { useAdjustmentOptions, useExtend, useMoreTime } from '../../api/adjust'
import { messageOf } from '../../api/client'
import { useProgress } from '../../api/plan'
import { formatDay } from '../../dates'
import { haptics } from '../../haptics'
import { colors, radius, space } from '../../theme'
import { Button, ErrorMessage, Spinner } from '../ui'
import { Card } from './Card'

/**
 * Shown while the next milestone doesn't fit: each fix with what it would change. Moving dates and adding time
 * are confirmed in a system dialog; dropping tasks opens its own sheet.
 */
export function BackOnTrack({ goalId }: { goalId: number }) {
  const progress = useProgress(goalId)
  const behind = (progress.data?.minutesBehind ?? 0) > 0
  const options = useAdjustmentOptions(goalId, behind)
  const extend = useExtend(goalId)
  const moreTime = useMoreTime(goalId)

  if (!behind) return null
  if (options.isPending) return <Card title="Get back on track"><Spinner /></Card>
  if (!options.data) return <Card title="Get back on track"><ErrorMessage>{messageOf(options.error)}</ErrorMessage></Card>

  const o = options.data
  if (o.minutesBehind === 0 || !o.milestone) return null
  const failed = extend.error ?? moreTime.error
  const busy = extend.isPending || moreTime.isPending

  function confirmExtend(days: number, milestoneDate: string, deadline: string) {
    Alert.alert('Move the dates?', `“${o.milestone!.title}” will be due ${formatDay(milestoneDate)}. Later milestones `
      + `and the deadline (${formatDay(deadline)}) move too.`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Move', onPress: () => extend.mutate(days, { onSuccess: haptics.success }) },
    ])
  }

  function confirmMoreTime(minutesPerDay: number) {
    Alert.alert(`${minutesPerDay} min a day?`, 'From tomorrow on; upcoming tasks move earlier.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Add time', onPress: () => moreTime.mutate(minutesPerDay, { onSuccess: haptics.success }) },
    ])
  }

  return (
    <Card title="Get back on track">
      <Text style={styles.text}>
        “{o.milestone.title}” needs {o.minutesBehind} more minutes than you have before{' '}
        {formatDay(o.milestone.targetDate)}. Pick a fix; nothing changes until you confirm.
      </Text>
      {failed && <ErrorMessage>{messageOf(failed)}</ErrorMessage>}
      {o.extend && (
        <Option title={`Move it by ${o.extend.days} ${o.extend.days === 1 ? 'day' : 'days'}`}
          detail={`Due ${formatDay(o.extend.milestoneDate)}; later milestones and the deadline `
            + `(${formatDay(o.extend.deadline)}) move too.`}
          action="Move dates" busy={extend.isPending} disabled={busy}
          onPress={() => confirmExtend(o.extend!.days, o.extend!.milestoneDate, o.extend!.deadline)} />
      )}
      {o.moreTime && (
        <Option title={`${o.moreTime.minutesPerDay} min a day`}
          detail={`Up from ${o.moreTime.currentMinutesPerDay} (+${o.moreTime.minutesPerDay
            - o.moreTime.currentMinutesPerDay}), from tomorrow on. Upcoming tasks move earlier.`}
          warning={o.moreTime.realistic ? undefined
            : 'That is a big jump from your usual time and may be hard to keep up.'}
          action="Add time" busy={moreTime.isPending} disabled={busy}
          onPress={() => confirmMoreTime(o.moreTime!.minutesPerDay)} />
      )}
      <Option title="Drop some tasks" detail="The coach suggests upcoming tasks you could skip, and why."
        action="Suggest" disabled={busy} onPress={() => router.push(`/goals/${goalId}/cut`)} />
    </Card>
  )
}

function Option({ title, detail, warning, action, busy, disabled, onPress }: {
  title: string; detail: string; warning?: string; action: string; busy?: boolean; disabled?: boolean
  onPress: () => void
}) {
  return (
    <View style={styles.option}>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.small}>{detail}</Text>
      {warning && <Text style={styles.warning}>{warning}</Text>}
      <View style={styles.action}>
        <Button title={action} variant="secondary" busy={busy} disabled={disabled} onPress={onPress} />
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  text: { fontSize: 14, lineHeight: 20, color: colors.textMuted },
  option: { padding: space.md, gap: space.xs, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.divider },
  title: { fontSize: 15, fontWeight: '600', color: colors.text },
  small: { fontSize: 13, lineHeight: 18, color: colors.textMuted },
  warning: { fontSize: 13, color: colors.warning },
  action: { marginTop: space.sm, alignSelf: 'flex-start' },
})
