import { HfSpace, HfSpacesApiResponse } from '../types';

export const HF_SPACES_BASE_URL = 'https://huggingface.co/spaces-json';
export const HF_SEMANTIC_SEARCH_BASE_URL = 'https://huggingface.co/api/spaces/semantic-search';

/**
 * Fetches paginated Hugging Face spaces using:
 * https://huggingface.co/spaces-json?p=${page}&includeNonRunning=true&withCount=false
 *
 * Uses the server proxy /api/spaces first, with direct fetch fallback.
 */
export async function fetchHfSpaces(page: number = 0, filter?: string, search?: string): Promise<{ spaces: HfSpace[]; pageIndex: number }> {
  const queryParams = new URLSearchParams({
    p: String(page),
    includeNonRunning: 'true',
    withCount: 'false',
  });

  if (filter && filter !== 'all') {
    queryParams.set('filter', filter);
  }
  if (search && search.trim()) {
    queryParams.set('search', search.trim());
  }

  const queryString = queryParams.toString();
  const proxyUrl = `/api/spaces?${queryString}`;
  const directUrl = `${HF_SPACES_BASE_URL}?${queryString}`;

  let response: Response | null = null;
  let fetchError: Error | null = null;

  // 1. Try server proxy first
  try {
    const res = await fetch(proxyUrl, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    if (res.ok) {
      response = res;
    } else {
      fetchError = new Error(`Proxy responded with HTTP ${res.status}`);
    }
  } catch (err: any) {
    fetchError = err;
  }

  // 2. Direct fallback
  if (!response) {
    try {
      const res = await fetch(directUrl, {
        method: 'GET',
        headers: { Accept: 'application/json' },
      });
      if (res.ok) {
        response = res;
      } else {
        throw new Error(`Direct fetch HTTP ${res.status}`);
      }
    } catch (err: any) {
      console.warn('Direct spaces-json fetch failed:', err);
      throw new Error(
        `Failed to fetch Hugging Face Spaces: ${fetchError?.message || err?.message || 'Network error'}`
      );
    }
  }

  const json: HfSpacesApiResponse = await response.json();

  if (json && Array.isArray(json.spaces)) {
    return {
      spaces: json.spaces,
      pageIndex: typeof json.pageIndex === 'number' ? json.pageIndex : page,
    };
  }

  if (Array.isArray(json)) {
    return {
      spaces: json as unknown as HfSpace[],
      pageIndex: page,
    };
  }

  throw new Error('Unexpected API response structure from Hugging Face spaces-json');
}

/**
 * Fetches spaces filtered via Hugging Face Semantic Search API:
 * https://huggingface.co/api/spaces/semantic-search?category=${category}&includeNonRunning=true
 *
 * Supports category filtering and semantic query parameter.
 */
export async function fetchHfSemanticSearch(options: {
  category?: string;
  query?: string;
}): Promise<HfSpace[]> {
  const queryParams = new URLSearchParams({
    includeNonRunning: 'true',
  });

  if (options.category && options.category !== 'all') {
    queryParams.set('category', options.category);
  }
  if (options.query && options.query.trim()) {
    queryParams.set('q', options.query.trim());
  }

  const queryString = queryParams.toString();
  const proxyUrl = `/api/spaces/semantic-search?${queryString}`;
  const directUrl = `${HF_SEMANTIC_SEARCH_BASE_URL}?${queryString}`;

  let response: Response | null = null;
  let fetchError: Error | null = null;

  // 1. Try server proxy
  try {
    const res = await fetch(proxyUrl, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    if (res.ok) {
      response = res;
    } else {
      fetchError = new Error(`Proxy responded with HTTP ${res.status}`);
    }
  } catch (err: any) {
    fetchError = err;
  }

  // 2. Direct fetch fallback
  if (!response) {
    try {
      const res = await fetch(directUrl, {
        method: 'GET',
        headers: { Accept: 'application/json' },
      });
      if (res.ok) {
        response = res;
      } else {
        throw new Error(`Direct fetch HTTP ${res.status}`);
      }
    } catch (err: any) {
      console.warn('Direct semantic search fetch failed:', err);
      throw new Error(
        `Failed to query Hugging Face Semantic Search: ${fetchError?.message || err?.message || 'Network error'}`
      );
    }
  }

  const json = await response.json();

  if (Array.isArray(json)) {
    return json as HfSpace[];
  }

  if (json && Array.isArray((json as any).spaces)) {
    return (json as any).spaces as HfSpace[];
  }

  throw new Error('Unexpected response format from Hugging Face semantic-search API');
}
