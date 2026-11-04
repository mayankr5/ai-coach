import { StyleSheet, Text, View } from 'react-native'
import { messageOf } from '../../api/client'
import { useTasks, type Task } from '../../api/plan'
import { addDays, formatDay, parseDate, today, toIsoDate } from '../../dates'
import { colors, space } from '../../theme'
import { ErrorMessage, Spinner } from '../ui'
import { Card } from './Card'

const DAYS = 7
const WEEKDAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

/** The next week day by day, so the user can see where catch-up work landed. */
export function UpcomingDays({ goalId, bufferDay }: { goalId: number; bufferDay: string | null }) {
  const from = toIsoDate(addDays(today(), 1))
  const to = toIsoDate(addDays(today(), DAYS))
  const tasks = useTasks(goalId, from, to)

  const byDay = new Map<string, Task[]>()
  for (let i = 1; i <= DAYS; i++) byDay.set(toIsoDate(addDays(today(), i)), [])
  tasks.data?.forEach(t => byDay.get(t.scheduledDate)?.push(t))

  return (
    <Card title="Next 7 days">
      {tasks.isPending && <Spinner label="Loading the week" />}
      {tasks.isError && !tasks.data && <ErrorMessage>{messageOf(tasks.error)}</ErrorMessage>}
      {tasks.data && [...byDay].map(([day, dayTasks]) => {
        const minutes = dayTasks.reduce((sum, t) => sum + t.estimatedMinutes, 0)
        const isBuffer = WEEKDAYS[parseDate(day).getDay()] === bufferDay
        return (
          <View key={day} style={styles.day}>
            <View style={styles.date}>
              <Text style={styles.dateText}>{formatDay(day)}</Text>
              {minutes > 0 && <Text style={styles.small}>{minutes} min</Text>}
            </View>
            <View style={styles.tasks}>
              {dayTasks.length === 0 && <Text style={styles.empty}>{isBuffer ? 'Buffer day' : 'Nothing planned'}</Text>}
              {dayTasks.map(t => (
                <Text key={t.id} style={styles.task}>
                  {t.title}
                  {isBuffer && <Text style={styles.catchUp}>  catch-up</Text>}
                </Text>
              ))}
            </View>
          </View>
        )
      })}
    </Card>
  )
}

const styles = StyleSheet.create({
  day: { flexDirection: 'row', gap: space.md },
  date: { width: 84 },
  dateText: { fontSize: 14, fontWeight: '600', color: colors.text },
  small: { fontSize: 12, color: colors.textMuted },
  tasks: { flex: 1, gap: space.xs },
  task: { fontSize: 14, lineHeight: 20, color: colors.text },
  empty: { fontSize: 14, color: colors.textFaint },
  catchUp: { fontSize: 12, color: colors.warning },
})
