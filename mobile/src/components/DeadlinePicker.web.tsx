// Web preview only: the native picker doesn't run in a browser, so this uses the browser's date input
import { createElement } from 'react'
import { StyleSheet, Text, View } from 'react-native'
import { deadlineLabel, toIsoDate } from '../dates'
import { colors, space } from '../theme'

export function DeadlinePicker({ value, minimumDate, onChange, error }: {
  value: string; minimumDate: Date; onChange: (iso: string) => void; error?: string
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>Deadline</Text>
      <View style={styles.row}>
        {createElement('input', {
          type: 'date', value, min: toIsoDate(minimumDate), 'aria-label': 'Deadline',
          onChange: (e: { target: { value: string } }) => e.target.value && onChange(e.target.value),
          style: { fontSize: 16, padding: 8, borderRadius: 8, border: `1px solid ${colors.border}` },
        })}
        <Text style={styles.left}>{deadlineLabel(value)}</Text>
      </View>
      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  )
}

const styles = StyleSheet.create({
  field: { gap: space.xs },
  label: { fontSize: 14, fontWeight: '500', color: colors.text },
  row: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  left: { fontSize: 14, color: colors.textMuted },
  error: { fontSize: 13, color: colors.danger },
})
