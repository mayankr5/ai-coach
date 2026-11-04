import { useState } from 'react'
import { Pressable, StyleSheet, Text, View } from 'react-native'
import { messageOf } from '../../api/client'
import { useCheckIn, useTasks, type Task, type TaskStatus } from '../../api/plan'
import { today, toIsoDate } from '../../dates'
import { haptics } from '../../haptics'
import { refreshReminders } from '../../reminders'
import { colors, radius, space } from '../../theme'
import { Button, ErrorMessage, Field, Spinner } from '../ui'
import { Card } from './Card'

type Result = Exclude<TaskStatus, 'PENDING'>

const RESULTS: { value: Result; label: string; color: string }[] = [
  { value: 'DONE', label: 'Done', color: colors.done },
  { value: 'PARTIAL', label: 'Partly', color: colors.partial },
  { value: 'SKIPPED', label: 'Skipped', color: colors.skipped },
]

/**
 * Today's tasks: mark each one, add a note, and check in. The backend re-plans and the coach's reply shows up
 * at the top of the goal screen, which reloads after a check-in.
 */
export function TodayCard({ goalId, checkedIn }: { goalId: number; checkedIn: boolean }) {
  const day = toIsoDate(today())
  const tasks = useTasks(goalId, day, day)

  return (
    <Card title="Today">
      {tasks.isPending && <Spinner label="Loading today's tasks" />}
      {tasks.isError && !tasks.data && <ErrorMessage>{messageOf(tasks.error)}</ErrorMessage>}
      {tasks.data && (
        // Remounts with fresh choices whenever the task list changes
        <CheckInForm key={tasks.data.map(t => `${t.id}:${t.status}`).join()} goalId={goalId} day={day}
          tasks={tasks.data} checkedIn={checkedIn} />
      )}
    </Card>
  )
}

function CheckInForm({ goalId, day, tasks, checkedIn }:
  { goalId: number; day: string; tasks: Task[]; checkedIn: boolean }) {
  const checkIn = useCheckIn(goalId)
  const [results, setResults] = useState<Record<number, Result>>(() =>
    Object.fromEntries(tasks.filter(t => t.status !== 'PENDING').map(t => [t.id, t.status as Result])))
  const [note, setNote] = useState('')
  const marked = Object.keys(results).length
  // Every task needs a result: once a day has a check-in, nothing marks its leftovers as skipped
  const ready = tasks.length > 0 ? marked === tasks.length : note.trim() !== ''
  const totalMinutes = tasks.reduce((sum, t) => sum + t.estimatedMinutes, 0)

  function mark(taskId: number, result: Result) {
    haptics.select()
    setResults(prev => ({ ...prev, [taskId]: result }))
  }

  function submit() {
    checkIn.mutate({
      date: day,
      tasks: Object.entries(results).map(([taskId, status]) => ({ taskId: Number(taskId), status })),
      note: note.trim() || undefined,
    }, {
      onSuccess: () => {
        haptics.success()
        // Checked in: today's reminder may no longer be needed
        void refreshReminders()
      },
    })
  }

  return (
    <View style={styles.form}>
      {tasks.length === 0 ? (
        <Text style={styles.muted}>
          Nothing planned today. Rest, or use the time to catch up; you can still leave a note for the coach.
        </Text>
      ) : (
        <>
          <Text style={styles.muted}>{totalMinutes} minutes planned. How did it go?</Text>
          {tasks.map(task => (
            <View key={task.id} style={styles.task}>
              <Text style={styles.taskTitle}>{task.title}</Text>
              <Text style={styles.small}>{task.estimatedMinutes} min</Text>
              <View style={styles.results}>
                {RESULTS.map(r => {
                  const selected = results[task.id] === r.value
                  return (
                    <Pressable key={r.value} accessibilityRole="radio" accessibilityState={{ checked: selected }}
                      accessibilityLabel={`${r.label}: ${task.title}`} onPress={() => mark(task.id, r.value)}
                      style={[styles.result, selected && { backgroundColor: r.color, borderColor: r.color }]}>
                      <Text style={[styles.resultText, selected && styles.resultTextSelected]}>{r.label}</Text>
                    </Pressable>
                  )
                })}
              </View>
            </View>
          ))}
        </>
      )}

      <Field label="Note for the coach" hint="(optional)" value={note} onChangeText={setNote} maxLength={2000}
        placeholder="What got in the way, or what went well?" multiline style={styles.note} />

      {checkIn.isError && <ErrorMessage>{messageOf(checkIn.error)}</ErrorMessage>}
      {tasks.length > 0 && !ready && (
        <Text style={styles.small}>Mark every task to check in ({marked} of {tasks.length}).</Text>
      )}
      <Button title={checkIn.isPending ? 'The coach is adjusting your plan…' : checkedIn ? 'Update check-in' : 'Check in'}
        busy={checkIn.isPending} disabled={!ready} onPress={submit} />
    </View>
  )
}

const styles = StyleSheet.create({
  form: { gap: space.md },
  muted: { fontSize: 14, color: colors.textMuted },
  small: { fontSize: 13, color: colors.textMuted },
  task: { gap: space.xs, paddingVertical: space.sm, borderTopWidth: 1, borderTopColor: colors.divider },
  taskTitle: { fontSize: 15, fontWeight: '500', color: colors.text },
  results: { flexDirection: 'row', gap: space.sm, marginTop: space.xs },
  result: {
    flex: 1, minHeight: 40, alignItems: 'center', justifyContent: 'center', borderRadius: radius.md,
    borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface,
  },
  resultText: { fontSize: 14, fontWeight: '500', color: colors.textMuted },
  resultTextSelected: { color: colors.onPrimary, fontWeight: '600' },
  note: { minHeight: 72, paddingTop: space.md, textAlignVertical: 'top' },
})
