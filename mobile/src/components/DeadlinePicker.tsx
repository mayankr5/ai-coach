import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker'
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native'
import { deadlineLabel, formatDay, parseDate, toIsoDate } from '../dates'
import { colors, radius, space } from '../theme'

interface Props {
  /** ISO date, e.g. "2027-02-05". */
  value: string
  minimumDate: Date
  onChange: (iso: string) => void
  error?: string
}

/**
 * The phone's own date picker: on iOS the compact button that opens a calendar, on Android the calendar dialog.
 * Days before {@code minimumDate} can't be picked.
 */
export function DeadlinePicker({ value, minimumDate, onChange, error }: Props) {
  const date = parseDate(value)
  const pick = (picked: Date) => onChange(toIsoDate(picked))
  return (
    <View style={styles.field}>
      <Text style={styles.label}>Deadline</Text>
      <View style={styles.row}>
        {Platform.OS === 'ios' ? (
          <DateTimePicker value={date} mode="date" display="compact" minimumDate={minimumDate}
            accentColor={colors.primary} themeVariant="light" onValueChange={(_, picked) => pick(picked)} />
        ) : (
          <Pressable accessibilityRole="button" accessibilityLabel={`Deadline ${formatDay(value)}`}
            style={styles.button}
            onPress={() => DateTimePickerAndroid.open({
              value: date, mode: 'date', minimumDate, onValueChange: (_, picked) => pick(picked),
            })}>
            <Text style={styles.buttonText}>{formatDay(value)}</Text>
          </Pressable>
        )}
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
  button: {
    minHeight: 44, paddingHorizontal: space.md, justifyContent: 'center', borderRadius: radius.md,
    borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface,
  },
  buttonText: { fontSize: 16, color: colors.text },
  left: { fontSize: 14, color: colors.textMuted },
  error: { fontSize: 13, color: colors.danger },
})
