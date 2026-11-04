import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Stack } from 'expo-router'
import * as SplashScreen from 'expo-splash-screen'
import { StatusBar } from 'expo-status-bar'
import { useEffect } from 'react'
import { AuthProvider } from '../auth/AuthProvider'
import { useAuth } from '../auth/useAuth'
import { colors } from '../theme'

SplashScreen.preventAutoHideAsync()

const queryClient = new QueryClient()

export default function RootLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <StatusBar style="dark" />
        <Routes />
      </AuthProvider>
    </QueryClientProvider>
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
    <Stack screenOptions={{
      headerTintColor: colors.primary,
      headerTitleStyle: { color: colors.text },
      contentStyle: { backgroundColor: colors.background },
    }}>
      <Stack.Protected guard={user !== null}>
        <Stack.Screen name="index" options={{ title: 'AI Coach' }} />
      </Stack.Protected>
      <Stack.Protected guard={user === null}>
        <Stack.Screen name="login" options={{ headerShown: false }} />
        <Stack.Screen name="register" options={{ title: 'Create account', headerBackTitle: 'Log in' }} />
      </Stack.Protected>
    </Stack>
  )
}
