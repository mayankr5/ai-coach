import { Alert, Linking, Platform, StyleSheet, Switch, Text, View } from 'react-native'
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { Card } from '../components/goal/Card'
import { formatTime, TimePicker } from '../components/TimePicker'
import { Button, Screen } from '../components/ui'
import {
  allowNotifications, DEFAULT_REMINDER, goalsToday, loadReminder, saveReminder, scheduleReminders,
  type ReminderSettings,
} from '../reminders'
import { colors, space } from '../theme'

export default function SettingsScreen() {
  const { user, logout } = useAuth()
  const [reminder, setReminder] = useState<ReminderSettings | null>(null)
  const [denied, setDenied] = useState(false)

  useEffect(() => {
    loadReminder().then(setReminder)
  }, [])

  async function update(next: ReminderSettings) {
    if (next.enabled && !(await allowNotifications())) {
      setDenied(true)
      return
    }
    setDenied(false)
    setReminder(next)
    await saveReminder(next)
    // Without a connection, today simply isn't skipped
    const goals = await goalsToday().catch(() => ({ allCheckedIn: false, url: '/' }))
    await scheduleReminders(next, goals)
  }

  const confirmLogout = () => Alert.alert('Log out?', 'Reminders on this phone stop too.', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Log out', style: 'destructive', onPress: () => void logout() },
  ])

  const r = reminder ?? DEFAULT_REMINDER
  return (
    <Screen>
      <View style={styles.sections}>
        <Card title="Daily reminder">
          {Platform.OS === 'web' ? (
            <Text style={styles.muted}>Reminders work in the iPhone and Android app.</Text>
          ) : (
            <>
              <View style={styles.row}>
                <Text style={styles.label}>Remind me to check in</Text>
                <Switch accessibilityLabel="Remind me to check in" value={r.enabled} disabled={!reminder}
                  trackColor={{ true: colors.primary }} onValueChange={enabled => void update({ ...r, enabled })} />
              </View>
              {r.enabled && (
                <View style={styles.row}>
                  <Text style={styles.label}>At</Text>
                  <TimePicker label="Reminder time" hour={r.hour} minute={r.minute}
                    onChange={(hour, minute) => void update({ ...r, hour, minute })} />
                </View>
              )}
              <Text style={styles.muted}>
                {r.enabled
                  ? `Every day at ${formatTime(r.hour, r.minute)}, unless you've already checked in on every goal.`
                  : 'One gentle nudge a day, skipped once you’ve checked in.'}
              </Text>
              {denied && (
                <View style={styles.denied}>
                  <Text style={styles.warning}>
                    Notifications are turned off for AI Coach. Allow them in Settings to get reminders.
                  </Text>
                  <Button title="Open Settings" variant="secondary" onPress={() => void Linking.openSettings()} />
                </View>
              )}
            </>
          )}
        </Card>

        <Card title="Account">
          <Text style={styles.label}>{user?.displayName}</Text>
          <Text style={styles.muted}>{user?.email}</Text>
          <Button title="Log out" variant="secondary" onPress={confirmLogout} />
        </Card>
      </View>
    </Screen>
  )
}

const styles = StyleSheet.create({
  sections: { gap: space.lg, paddingTop: space.sm },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.md },
  label: { fontSize: 15, fontWeight: '500', color: colors.text },
  muted: { fontSize: 13, lineHeight: 18, color: colors.textMuted },
  denied: { gap: space.sm },
  warning: { fontSize: 13, lineHeight: 18, color: colors.warning },
})
