import AsyncStorage from '@react-native-async-storage/async-storage'
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister'
import { QueryClient } from '@tanstack/react-query'

/** Saved data older than this isn't shown, even offline. */
const MAX_AGE = 7 * 24 * 60 * 60 * 1000

// Kept in memory as long as it may be saved, or restored entries would be dropped straight away
export const queryClient = new QueryClient({ defaultOptions: { queries: { gcTime: MAX_AGE } } })

/**
 * The last data loaded is saved on the phone (app storage, not encrypted: goals and tasks only, never tokens),
 * so the plan still shows without a connection. It is deleted on logout.
 */
const persister = createAsyncStoragePersister({ storage: AsyncStorage, key: 'aiCoach.cache' })

export const persistOptions = { persister, maxAge: MAX_AGE, buster: 'v1' }
