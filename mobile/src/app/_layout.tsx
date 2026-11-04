import { focusManager } from '@tanstack/react-query'
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client'
import * as Notifications from 'expo-notifications'
import { Stack } from 'expo-router'
import * as SplashScreen from 'expo-splash-screen'
import { StatusBar } from 'expo-status-bar'
import { useEffect } from 'react'
import { AppState, Platform } from 'react-native'
import { persistOptions, queryClient } from '../api/queryClient'
import { AuthProvider } from '../auth/AuthProvider'
import { useAuth } from '../auth/useAuth'
import { Reminders } from '../components/Reminders'
import { colors } from '../theme'

SplashScreen.preventAutoHideAsync()

// Coming back to the app counts as focus, so stale data (e.g. yesterday's tasks) refetches
if (Platform.OS !== 'web') {
  AppState.addEventListener('change', state => focusManager.setFocused(state === 'active'))
  // A reminder that arrives while the app is open still shows as a banner
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true, shouldShowList: true, shouldPlaySound: false, shouldSetBadge: false,
    }),
  })
}

export default function RootLayout() {
  return (
    <PersistQueryClientProvider client={queryClient} persistOptions={persistOptions}>
      <AuthProvider>
        <StatusBar style="dark" />
        <Routes />
      </AuthProvider>
    </PersistQueryClientProvider>
  )
}

/** Logged-in screens and auth screens never mix: when the session starts or ends, the other set is shown. */
function Routes() {
  const { user, restoring } = useAuth()

  // The splash stays up until a saved session has been checked, so the login screen doesn't flash
  useEffect(() => {
    if (!restoring) SplashScreen.hideAsync()
  }, [restoring])
  if (restoring) return null

  return (
    <>
      {user && Platform.OS !== 'web' && <Reminders />}
      <Stack screenOptions={{
        headerTintColor: colors.primary,
        headerTitleStyle: { color: colors.text },
        contentStyle: { backgroundColor: colors.background },
      }}>
        <Stack.Protected guard={user !== null}>
          <Stack.Screen name="index" options={{ title: 'Your goals' }} />
          <Stack.Screen name="goals/new" options={{ presentation: 'modal', title: 'New goal' }} />
          <Stack.Screen name="goals/[id]/index" options={{ title: 'Goal', headerBackTitle: 'Goals' }} />
          <Stack.Screen name="goals/[id]/cut" options={{ presentation: 'modal', title: 'Drop some tasks' }} />
          <Stack.Screen name="settings" options={{ title: 'Settings', headerBackTitle: 'Goals' }} />
        </Stack.Protected>
        <Stack.Protected guard={user === null}>
          <Stack.Screen name="login" options={{ headerShown: false }} />
          <Stack.Screen name="register" options={{ title: 'Create account', headerBackTitle: 'Log in' }} />
        </Stack.Protected>
      </Stack>
    </>
  )
}
