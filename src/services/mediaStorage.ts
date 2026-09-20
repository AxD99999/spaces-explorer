import { GeneratedMediaItem, GeneratedMediaType, HfSpace } from '../types';

const DB_NAME = 'hf_spaces_studio_db';
const DB_VERSION = 1;
const STORE_NAME = 'generated_media';
const LOCAL_STORAGE_KEY = 'hf_generated_media_fallback';

// Initialize or open IndexedDB
function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB not supported in this environment'));
      return;
    }

    const request = window.indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        store.createIndex('createdAt', 'createdAt', { unique: false });
        store.createIndex('type', 'type', { unique: false });
        store.createIndex('spaceId', 'spaceId', { unique: false });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('Failed to open database'));
  });
}

// Fallback for localStorage
function getFromLocalStorage(): GeneratedMediaItem[] {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveToLocalStorage(items: GeneratedMediaItem[]) {
  try {
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(items));
  } catch (e) {
    console.warn('LocalStorage quota exceeded for media storage', e);
  }
}

// Save a media item permanently
export async function saveMediaItem(item: GeneratedMediaItem): Promise<void> {
  try {
    const db = await openDb();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.put(item);

      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  } catch {
    // Fallback to localStorage
    const current = getFromLocalStorage();
    const updated = [item, ...current.filter((i) => i.id !== item.id)];
    saveToLocalStorage(updated);
  }
}

// Retrieve all media items sorted by newest first
export async function getAllMediaItems(): Promise<GeneratedMediaItem[]> {
  try {
    const db = await openDb();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const req = store.getAll();

      req.onsuccess = () => {
        const results = (req.result as GeneratedMediaItem[]) || [];
        // Sort descending by createdAt
        results.sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        resolve(results);
      };
      req.onerror = () => reject(req.error);
    });
  } catch {
    return getFromLocalStorage().sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );
  }
}

// Delete an individual media item
export async function deleteMediaItem(id: string): Promise<void> {
  try {
    const db = await openDb();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.delete(id);

      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  } catch {
    const current = getFromLocalStorage();
    saveToLocalStorage(current.filter((i) => i.id !== id));
  }
}

// Clear all media items
export async function clearAllMedia(): Promise<void> {
  try {
    const db = await openDb();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.clear();

      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  } catch {
    saveToLocalStorage([]);
  }
}

// Convert image remote URL to persistent Base64 DataURL
export async function convertUrlToDataUrl(url: string): Promise<string> {
  if (url.startsWith('data:')) return url;
  try {
    const response = await fetch(url, { mode: 'cors' });
    const blob = await response.blob();
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  } catch {
    // If CORS or offline, return original URL
    return url;
  }
}

// Helper to inspect raw space inference output and extract saved media objects
export function extractMediaItemsFromOutput(
  data: any,
  space: HfSpace,
  endpoint: string,
  prompt?: string,
  elapsedSeconds?: number,
  subdomain?: string
): GeneratedMediaItem[] {
  const items: GeneratedMediaItem[] = [];
  const spaceSubdomain = subdomain || space.subdomain || space.id.replace('/', '-').toLowerCase();

  const resolveUrl = (raw: string) => {
    if (!raw) return '';
    if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('data:')) return raw;
    if (raw.startsWith('/')) return `https://${spaceSubdomain}.hf.space${raw}`;
    return `https://${spaceSubdomain}.hf.space/gradio_api/file=${raw}`;
  };

  const processSingle = (val: any, index: number) => {
    if (!val) return;

    // 1. Gradio FileData object
    if (typeof val === 'object' && (val.url || val.path)) {
      const fileUrl = resolveUrl(val.url || val.path);
      const mime = (val.mime_type || '').toLowerCase();
      let type: GeneratedMediaType = 'other';

      if (mime.includes('image') || fileUrl.match(/\.(png|jpg|jpeg|webp|gif|svg)$/i)) {
        type = 'image';
      } else if (mime.includes('video') || fileUrl.match(/\.(mp4|webm|mov|mkv)$/i)) {
        type = 'video';
      } else if (mime.includes('audio') || fileUrl.match(/\.(mp3|wav|ogg|flac|m4a)$/i)) {
        type = 'audio';
      }

      items.push({
        id: `media_${Date.now()}_${Math.random().toString(36).substring(2, 7)}_${index}`,
        type,
        url: fileUrl,
        title: val.orig_name || `${space.title || space.id} output`,
        prompt,
        spaceId: space.id,
        spaceTitle: space.title || space.id,
        spaceEmoji: space.emoji || '🤗',
        createdAt: new Date().toISOString(),
        metadata: {
          endpoint,
          elapsedSeconds,
          mimeType: val.mime_type,
          size: val.size,
        },
      });
      return;
    }

    // 2. String check (URL or text content)
    if (typeof val === 'string') {
      const trimmed = val.trim();
      if (!trimmed) return;

      const isImgUrl = trimmed.match(/\.(png|jpg|jpeg|webp|gif)$/i) || trimmed.startsWith('data:image/');
      const isVideoUrl = trimmed.match(/\.(mp4|webm|mov)$/i) || trimmed.startsWith('data:video/');
      const isAudioUrl = trimmed.match(/\.(mp3|wav|ogg)$/i) || trimmed.startsWith('data:audio/');

      if (isImgUrl) {
        items.push({
          id: `media_${Date.now()}_${Math.random().toString(36).substring(2, 7)}_${index}`,
          type: 'image',
          url: resolveUrl(trimmed),
          title: prompt ? prompt.slice(0, 40) : `${space.title || space.id} image`,
          prompt,
          spaceId: space.id,
          spaceTitle: space.title || space.id,
          spaceEmoji: space.emoji || '🤗',
          createdAt: new Date().toISOString(),
          metadata: { endpoint, elapsedSeconds },
        });
      } else if (isVideoUrl) {
        items.push({
          id: `media_${Date.now()}_${Math.random().toString(36).substring(2, 7)}_${index}`,
          type: 'video',
          url: resolveUrl(trimmed),
          title: prompt ? prompt.slice(0, 40) : `${space.title || space.id} video`,
          prompt,
          spaceId: space.id,
          spaceTitle: space.title || space.id,
          spaceEmoji: space.emoji || '🤗',
          createdAt: new Date().toISOString(),
          metadata: { endpoint, elapsedSeconds },
        });
      } else if (isAudioUrl) {
        items.push({
          id: `media_${Date.now()}_${Math.random().toString(36).substring(2, 7)}_${index}`,
          type: 'audio',
          url: resolveUrl(trimmed),
          title: prompt ? prompt.slice(0, 40) : `${space.title || space.id} audio`,
          prompt,
          spaceId: space.id,
          spaceTitle: space.title || space.id,
          spaceEmoji: space.emoji || '🤗',
          createdAt: new Date().toISOString(),
          metadata: { endpoint, elapsedSeconds },
        });
      } else {
        // Text output (only save meaningful text)
        items.push({
          id: `media_${Date.now()}_${Math.random().toString(36).substring(2, 7)}_${index}`,
          type: 'text',
          textContent: trimmed,
          title: prompt ? prompt.slice(0, 40) : `${space.title || space.id} text`,
          prompt,
          spaceId: space.id,
          spaceTitle: space.title || space.id,
          spaceEmoji: space.emoji || '🤗',
          createdAt: new Date().toISOString(),
          metadata: { endpoint, elapsedSeconds },
        });
      }
      return;
    }

    // 3. Array / Gallery
    if (Array.isArray(val)) {
      val.forEach((sub, subIdx) => processSingle(sub, subIdx));
      return;
    }

    // 4. Other objects
    if (typeof val === 'object') {
      items.push({
        id: `media_${Date.now()}_${Math.random().toString(36).substring(2, 7)}_${index}`,
        type: 'other',
        textContent: JSON.stringify(val, null, 2),
        title: `${space.title || space.id} JSON response`,
        prompt,
        spaceId: space.id,
        spaceTitle: space.title || space.id,
        spaceEmoji: space.emoji || '🤗',
        createdAt: new Date().toISOString(),
        metadata: { endpoint, elapsedSeconds },
      });
    }
  };

  if (Array.isArray(data)) {
    data.forEach((item, idx) => processSingle(item, idx));
  } else {
    processSingle(data, 0);
  }

  return items;
}
