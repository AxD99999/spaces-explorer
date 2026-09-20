import { HfSpace } from './types';

/**
 * Returns a human-friendly space type / category for any Hugging Face space.
 * Examples: 'Image Generation', 'Image Editing', 'Video Generation', 'Text Generation', etc.
 */
export function getSpaceType(space: HfSpace): string {
  if (space.ai_category && space.ai_category.trim()) {
    return space.ai_category.trim();
  }

  // Check tags for category keywords
  if (space.tags && Array.isArray(space.tags)) {
    for (const tag of space.tags) {
      const lower = tag.toLowerCase();
      if (
        lower.includes('text-to-image') ||
        lower.includes('image-generation') ||
        lower.includes('diffusion') ||
        lower.includes('flux')
      ) {
        return 'Image Generation';
      }
      if (
        lower.includes('image-to-image') ||
        lower.includes('image-editing') ||
        lower.includes('inpaint') ||
        lower.includes('upscal')
      ) {
        return 'Image Editing';
      }
      if (
        lower.includes('text-to-video') ||
        lower.includes('video-generation') ||
        lower.includes('video')
      ) {
        return 'Video Generation';
      }
      if (lower.includes('text-generation') || lower.includes('llm')) {
        return 'Text Generation';
      }
      if (lower.includes('chat') || lower.includes('conversational')) {
        return 'Chatbots';
      }
      if (
        lower.includes('text-to-speech') ||
        lower.includes('audio') ||
        lower.includes('voice') ||
        lower.includes('music')
      ) {
        return 'Audio & Music';
      }
    }
  }

  // Check title, short description, or id for categorization clues
  const text = `${space.title || ''} ${space.id} ${space.shortDescription || ''} ${
    space.ai_short_description || ''
  }`.toLowerCase();

  if (
    text.includes('image edit') ||
    text.includes('identity-edit') ||
    text.includes('photo edit') ||
    text.includes('krea') ||
    text.includes('inpaint') ||
    text.includes('retouch') ||
    text.includes('remove background')
  ) {
    return 'Image Editing';
  }
  if (
    text.includes('image gen') ||
    text.includes('text-to-image') ||
    text.includes('diffusion') ||
    text.includes('flux') ||
    text.includes('dlss') ||
    text.includes('generator') ||
    text.includes('generate image') ||
    text.includes('drawing')
  ) {
    return 'Image Generation';
  }
  if (
    text.includes('video') ||
    text.includes('animate') ||
    text.includes('motion') ||
    text.includes('minimax') ||
    text.includes('film')
  ) {
    return 'Video Generation';
  }
  if (
    text.includes('chat') ||
    text.includes('bot') ||
    text.includes('agent') ||
    text.includes('assistant') ||
    text.includes('dialogue')
  ) {
    return 'Chatbots';
  }
  if (
    text.includes('music') ||
    text.includes('audio') ||
    text.includes('song') ||
    text.includes('speech') ||
    text.includes('tts') ||
    text.includes('voice')
  ) {
    return 'Audio & Music';
  }
  if (
    text.includes('text') ||
    text.includes('write') ||
    text.includes('llm') ||
    text.includes('translate') ||
    text.includes('summariz')
  ) {
    return 'Text Generation';
  }
  if (text.includes('ocr') || text.includes('pdf') || text.includes('document')) {
    return 'OCR & Documents';
  }
  if (text.includes('3d') || text.includes('mesh') || text.includes('gaussian') || text.includes('splat')) {
    return '3D Modeling';
  }
  if (text.includes('vision') || text.includes('detect') || text.includes('yolo') || text.includes('segment')) {
    return 'Computer Vision';
  }

  return 'AI Space';
}

/**
 * Returns tailored Tailwind CSS classes for the space type badge,
 * supporting dark and light themes with subtle tints and crisp text.
 */
export function getSpaceTypeStyle(type: string): string {
  const lower = type.toLowerCase();
  if (lower.includes('image gen') || lower.includes('diffusion') || lower.includes('flux')) {
    return 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/25';
  }
  if (lower.includes('image edit') || lower.includes('editing')) {
    return 'bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border-indigo-500/25';
  }
  if (lower.includes('video')) {
    return 'bg-pink-500/10 text-pink-700 dark:text-pink-300 border-pink-500/25';
  }
  if (lower.includes('text gen') || lower.includes('llm')) {
    return 'bg-sky-500/10 text-sky-700 dark:text-sky-300 border-sky-500/25';
  }
  if (lower.includes('chat') || lower.includes('assistant') || lower.includes('bot')) {
    return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/25';
  }
  if (lower.includes('music') || lower.includes('audio') || lower.includes('speech')) {
    return 'bg-cyan-500/10 text-cyan-700 dark:text-cyan-300 border-cyan-500/25';
  }
  if (lower.includes('ocr') || lower.includes('doc')) {
    return 'bg-teal-500/10 text-teal-700 dark:text-teal-300 border-teal-500/25';
  }
  if (lower.includes('3d')) {
    return 'bg-orange-500/10 text-orange-700 dark:text-orange-300 border-orange-500/25';
  }
  return 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700';
}
