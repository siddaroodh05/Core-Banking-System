const ENV = import.meta.env || {};
const BASE = (ENV.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(status, message, code, responseBody) {
    super(message);
    this.status = status;
    this.code = code;
    this.responseBody = responseBody;
  }
}

let onUnauthorized = () => {};
export function setUnauthorizedHandler(fn) { onUnauthorized = fn; }

function toQuery(query) {
  if (!query) return '';
  const p = new URLSearchParams();
  Object.entries(query).forEach(([k, v]) => v !== undefined && v !== null && p.append(k, v));
  const s = p.toString();
  return s ? `?${s}` : '';
}


function readMessage(body, fallback) {
  if (!body) return fallback;
  if (typeof body === 'string') return body;
  return body.message
    || body.failureReason
    || body.reason
    || body.errorMessage
    || body.data?.message
    || body.data?.failureReason
    || body.error
    || fallback;
}

async function send(path, { method = 'GET', body, query } = {}) {
  const res = await fetch(`${BASE}${path}${toQuery(query)}`, {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { res, data };
}

let refreshing = null;
function refreshSession() {
  if (!refreshing) {
    refreshing = send('/Auth/refresh', { method: 'POST' })
      .then(({ res }) => res.ok)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

export async function request(path, opts = {}) {
  let { res, data } = await send(path, opts);
  const isAuthRoute = path.startsWith('/Auth/');

  if (res.status === 401 && !isAuthRoute) {
    // Access token lasts 15 minutes; try one silent refresh, then replay the request.
    if (await refreshSession()) ({ res, data } = await send(path, opts));
    if (res.status === 401) {
      onUnauthorized();
      throw new ApiError(401, 'Your session has ended. Sign in again.');
    }
  }
  if (res.status === 429) throw new ApiError(429, 'Too many requests. Wait a moment and try again.');
  if (!res.ok) throw new ApiError(res.status, readMessage(data, 'Something went wrong. Try again.'), data?.error, data);
  return data;
}
