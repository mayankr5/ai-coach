import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { vi } from 'vitest'

/** Renders {@code page} at {@code path}; other routes show their path, so tests can see where it navigated. */
export function renderPage(page: ReactElement, { path = '/', url = path }: { path?: string; url?: string } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path={path} element={page} />
          <Route path="*" element={<CurrentPath />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function CurrentPath() {
  return <p>At {useLocation().pathname}</p>
}

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

/** Answers each request with {@code respond}, and records them. */
export function mockApi(respond: (request: Request) => Response | Promise<Response>) {
  const fetchMock = vi.fn(async (request: Request) => respond(request))
  vi.stubGlobal('fetch', fetchMock)
  return { requests: () => fetchMock.mock.calls.map(c => c[0]) }
}
