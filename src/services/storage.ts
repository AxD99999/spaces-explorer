import { HfSpace } from '../types';

const STORAGE_KEY_SPACES = 'hf_spaces_cache_v1';
const STORAGE_KEY_TIMESTAMP = 'hf_spaces_timestamp';

/**
 * Retrieves the stored spaces from device's local storage.
 * Returns null if no spaces have been loaded yet (initial empty state).
 */
export function getStoredSpaces(): HfSpace[] | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_SPACES);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed;
    }
    return null;
  } catch (error) {
    console.error('Failed to read spaces from device storage:', error);
    return null;
  }
}

/**
 * Stores the list and details of all spaces to device storage.
 */
export function saveStoredSpaces(spaces: HfSpace[]): boolean {
  try {
    localStorage.setItem(STORAGE_KEY_SPACES, JSON.stringify(spaces));
    localStorage.setItem(STORAGE_KEY_TIMESTAMP, Date.now().toString());
    return true;
  } catch (error) {
    console.error('Failed to persist spaces to device storage:', error);
    return false;
  }
}

/**
 * Gets the timestamp of when spaces were last saved to device storage.
 */
export function getLastStoredTimestamp(): number | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_TIMESTAMP);
    if (!raw) return null;
    const num = parseInt(raw, 10);
    return isNaN(num) ? null : num;
  } catch {
    return null;
  }
}

/**
 * Clears the stored spaces from device storage to return to initial empty state.
 */
export function clearStoredSpaces(): void {
  try {
    localStorage.removeItem(STORAGE_KEY_SPACES);
    localStorage.removeItem(STORAGE_KEY_TIMESTAMP);
  } catch (error) {
    console.error('Failed to clear spaces storage:', error);
  }
}
