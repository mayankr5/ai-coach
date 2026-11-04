import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker'
import { Platform, Pressable, StyleSheet, Text } from 'react-native'
import { colors, radius, space } from '../theme'

interface Props {
  label: string
  hour: number
  minute: number
  onChange: (hour: number, minute: number) => void
}

/** The phone's own time picker: a compact button on iOS, the clock dialog on Android. */
export function TimePicker({ label, hour, minute, onChange }: Props) {
  const value = new Date(2000, 0, 1, hour, minute)
  const pick = (picked: Date) => onChange(picked.getHours(), picked.getMinutes())
  if (Platform.OS === 'ios') {
    return (
      <DateTimePicker accessibilityLabel={label} value={value} mode="time" display="compact"
        accentColor={colors.primary} themeVariant="light" onValueChange={(_, picked) => pick(picked)} />
    )
  }
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${label} ${formatTime(hour, minute)}`}
      style={styles.button}
      onPress={() => DateTimePickerAndroid.open({ value, mode: 'time', onValueChange: (_, picked) => pick(picked) })}>
      <Text style={styles.text}>{formatTime(hour, minute)}</Text>
    </Pressable>
  )
}

export function formatTime(hour: number, minute: number): string {
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })
    .format(new Date(2000, 0, 1, hour, minute))
}

const styles = StyleSheet.create({
  button: {
    minHeight: 40, paddingHorizontal: space.md, justifyContent: 'center', borderRadius: radius.md,
    borderWidth: 1, borderColor: colors.border, backgroundColor: colors.surface,
  },
  text: { fontSize: 16, color: colors.text },
})
