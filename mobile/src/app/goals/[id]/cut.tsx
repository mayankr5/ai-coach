import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useEffect, useRef, useState } from 'react'
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native'
import { useCutScope, useSuggestCut, type CutSuggestion } from '../../../api/adjust'
import { ApiError, messageOf } from '../../../api/client'
import { Button, ErrorMessage, Screen, Spinner } from '../../../components/ui'
import { formatDay } from '../../../dates'
import { haptics } from '../../../haptics'
import { colors, radius, space } from '../../../theme'

/** The coach suggests upcoming tasks to drop, with reasons; the user unticks any to keep, then confirms. */
export default function CutScreen() {
  const goalId = Number(useLocalSearchParams<{ id: string }>().id)
  const suggest = useSuggestCut(goalId)
  // Asked once per opening: each suggestion is an AI call
  const asked = useRef(false)
  useEffect(() => {
    if (asked.current) return
    asked.current = true
    suggest.mutate()
  }, [suggest])

  return (
    <Screen>
      <Stack.Screen options={{
        title: 'Drop some tasks',
        headerLeft: () => (
          <Pressable accessibilityRole="button" hitSlop={8} onPress={() => router.back()}>
            <Text style={styles.cancel}>Cancel</Text>
          </Pressable>
        ),
      }} />
      {suggest.isError ? (
        <View style={styles.list}>
          <ErrorMessage>
            {suggest.error instanceof ApiError && suggest.error.status === 502
              ? 'The coach couldn’t suggest anything right now. Please try again in a minute.'
              : messageOf(suggest.error)}
          </ErrorMessage>
          <Button title="Try again" variant="secondary" onPress={() => suggest.mutate()} />
        </View>
      ) : suggest.data ? <Choose goalId={goalId} suggestion={suggest.data} />
        : <Spinner label="The coach is picking tasks, this can take up to a minute" />}
    </Screen>
  )
}

function Choose({ goalId, suggestion }: { goalId: number; suggestion: CutSuggestion }) {
  const cut = useCutScope(goalId)
  const [ticked, setTicked] = useState(() => new Set(suggestion.tasks.map(t => t.taskId)))
  const freed = suggestion.tasks.filter(t => ticked.has(t.taskId)).reduce((sum, t) => sum + t.minutes, 0)
  const enough = freed >= suggestion.minutesBehind
  const label = `Drop ${ticked.size} ${ticked.size === 1 ? 'task' : 'tasks'}`

  function flip(id: number) {
    haptics.select()
    setTicked(prev => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }

  function confirm() {
    Alert.alert(`${label}?`, `They’re removed from your plan and the rest moves earlier. This frees ${freed} minutes.`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Drop', style: 'destructive', onPress: () => cut.mutate([...ticked], {
          onSuccess: () => {
            haptics.success()
            router.back()
          },
        }),
      },
    ])
  }

  return (
    <View style={styles.list}>
      <Text style={styles.intro}>
        You’re {suggestion.minutesBehind} minutes short. The coach suggests these; untick any you want to keep.
      </Text>
      {suggestion.tasks.map(t => {
        const checked = ticked.has(t.taskId)
        return (
          <Pressable key={t.taskId} accessibilityRole="checkbox" accessibilityState={{ checked }}
            accessibilityLabel={t.title} onPress={() => flip(t.taskId)}
            style={[styles.task, checked && styles.taskChecked]}>
            <View style={[styles.box, checked && styles.boxChecked]}>
              {checked && <Text style={styles.tick}>✓</Text>}
            </View>
            <View style={styles.taskText}>
              <Text style={styles.title}>{t.title}</Text>
              <Text style={styles.small}>{t.minutes} min · {formatDay(t.scheduledDate)}</Text>
              <Text style={styles.reason}>{t.reason}</Text>
            </View>
          </Pressable>
        )
      })}
      <Text style={[styles.total, { color: enough ? colors.success : colors.warning }]}>
        Frees {freed} of the {suggestion.minutesBehind} minutes needed.
      </Text>
      {cut.isError && <ErrorMessage>{messageOf(cut.error)}</ErrorMessage>}
      <Button title={cut.isPending ? 'Dropping…' : label} busy={cut.isPending} disabled={ticked.size === 0}
        onPress={confirm} />
    </View>
  )
}

const styles = StyleSheet.create({
  cancel: { fontSize: 17, color: colors.primary },
  list: { gap: space.md, paddingBottom: space.xl },
  intro: { fontSize: 14, lineHeight: 20, color: colors.textMuted },
  task: {
    flexDirection: 'row', gap: space.md, padding: space.md, borderRadius: radius.lg, borderWidth: 1,
    borderColor: colors.divider, backgroundColor: colors.surface,
  },
  taskChecked: { borderColor: colors.primary },
  box: {
    width: 22, height: 22, marginTop: 1, borderRadius: 6, borderWidth: 2, borderColor: colors.border,
    alignItems: 'center', justifyContent: 'center',
  },
  boxChecked: { backgroundColor: colors.primary, borderColor: colors.primary },
  tick: { color: colors.onPrimary, fontSize: 14, fontWeight: '700', lineHeight: 16 },
  taskText: { flex: 1, gap: 2 },
  title: { fontSize: 15, fontWeight: '600', color: colors.text },
  small: { fontSize: 13, color: colors.textMuted },
  reason: { fontSize: 13, lineHeight: 18, color: colors.text, marginTop: 2 },
  total: { fontSize: 14, fontWeight: '500' },
})
